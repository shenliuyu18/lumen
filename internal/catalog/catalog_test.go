package catalog

import "testing"

func TestSeedSnapshots(t *testing.T) {
	seed, err := Snapshot()
	if err != nil || len(seed.Articles) == 0 {
		t.Fatalf("snapshot missing: %v", err)
	}
	newsroom, err := NewsroomSnapshot()
	if err != nil || newsroom == nil || len(newsroom.Groups) == 0 {
		t.Fatalf("newsroom snapshot missing: %v", err)
	}
}
