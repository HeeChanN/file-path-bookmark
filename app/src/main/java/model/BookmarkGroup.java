package model;

import java.util.ArrayList;
import java.util.List;

public class BookmarkGroup {
    private long id;
    private String name;
    private final List<Bookmark> bookmarks = new ArrayList<>();

    public BookmarkGroup() {}

    public BookmarkGroup(String name, long id) {
        this.id = id;
        this.name = name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public long getId() {
        return id;
    }

    public void include(Bookmark bookmark) {
        bookmarks.add(bookmark);
    }

    public boolean exclude(long id) {
        return bookmarks.removeIf(bookmark -> bookmark.getId() == id);
    }

    public List<Bookmark> getBookmarks() {
        return bookmarks;
    }

    public String getName() {
        return name;
    }
}
