package feed

import (

	"github.com/mmcdole/gofeed"

	"lumen/internal/model"
	"lumen/internal/rank"
)

func Parse(data []byte, source model.Source, fetchedAt int64, limit int) ([]model.Article, error) {
	parser := gofeed.NewParser()
	parsed, err := parser.ParseString(string(data))
	if err != nil {
		return nil, err
	}
	items := parsed.Items
	if limit > 0 && len(items) > limit {
		items = items[:limit]
	}
	articles := make([]model.Article, 0, len(items))
	for _, item := range items {
		description := item.Description
		if description == "" {
			description = item.Content
		}
		var published *int64
		if item.PublishedParsed != nil {
			value := item.PublishedParsed.UnixMilli()
			published = &value
		} else if item.UpdatedParsed != nil {
			value := item.UpdatedParsed.UnixMilli()
			published = &value
		}
		article := rank.Normalize(rank.NormalizeInput{
			Title:       item.Title,
			Link:        item.Link,
			Description: description,
			PublishedAt: published,
		}, source, fetchedAt)
		if article != nil {
			articles = append(articles, *article)
		}
	}
	return articles, nil
}
