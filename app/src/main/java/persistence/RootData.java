package persistence;

import model.BookmarkGroup;

import java.util.ArrayList;
import java.util.List;

public class RootData {
    private final List<BookmarkGroup> groups = new ArrayList<>();

    public List<BookmarkGroup> groups() {
        return groups;
    }
}
