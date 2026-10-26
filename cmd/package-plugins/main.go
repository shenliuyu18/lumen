package main

import (
	"archive/zip"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"os"
	"path/filepath"
)

type manifest struct {
	ID      string `json:"id"`
	Version string `json:"version"`
}

func main() {
	root := flag.String("root", ".", "repository root")
	out := flag.String("out", "dist/plugins", "output directory")
	flag.Parse()
	if err := os.MkdirAll(*out, 0o755); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	entries, err := os.ReadDir(filepath.Join(*root, "plugins"))
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	count := 0
	for _, entry := range entries {
		if !entry.IsDir() || entry.Name() == "runtime" {
			continue
		}
		dir := filepath.Join(*root, "plugins", entry.Name())
		data, err := os.ReadFile(filepath.Join(dir, "plugin.json"))
		if err != nil {
			continue
		}
		var info manifest
		if err := json.Unmarshal(data, &info); err != nil || info.ID == "" || info.Version == "" {
			fmt.Fprintf(os.Stderr, "skip %s: invalid plugin.json\n", entry.Name())
			continue
		}
		target := filepath.Join(*out, fmt.Sprintf("%s-%s.zip", info.ID, info.Version))
		if err := packageDir(dir, target); err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
		fmt.Println(target)
		count++
	}
	if count == 0 {
		fmt.Fprintln(os.Stderr, "no plugin packages were built")
		os.Exit(1)
	}
}

func packageDir(source, target string) error {
	file, err := os.Create(target)
	if err != nil {
		return err
	}
	writer := zip.NewWriter(file)
	walkErr := filepath.Walk(source, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}
		if info.IsDir() {
			return nil
		}
		relative, err := filepath.Rel(source, path)
		if err != nil {
			return err
		}
		header, err := zip.FileInfoHeader(info)
		if err != nil {
			return err
		}
		header.Name = filepath.ToSlash(relative)
		header.Method = zip.Deflate
		entry, err := writer.CreateHeader(header)
		if err != nil {
			return err
		}
		input, err := os.Open(path)
		if err != nil {
			return err
		}
		_, copyErr := io.Copy(entry, input)
		input.Close()
		return copyErr
	})
	if walkErr != nil {
		writer.Close()
		file.Close()
		return walkErr
	}
	if err := writer.Close(); err != nil {
		file.Close()
		return err
	}
	return file.Close()
}
