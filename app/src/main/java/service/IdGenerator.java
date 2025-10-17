package service;

import model.Bookmark;
import model.BookmarkGroup;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class IdGenerator {
    private final AtomicLong seqGroup;
    private final AtomicLong seqBookmark;

    private IdGenerator(long startGroup, long startBookmark) {
        this.seqGroup = new AtomicLong(startGroup);
        this.seqBookmark = new AtomicLong(startBookmark);
    }

    public long nextGroupId() {
        return seqGroup.getAndIncrement();
    }

    public long nextBookmarkId() {
        return seqBookmark.getAndIncrement();
    }

    public static IdGenerator fromExisting(List<BookmarkGroup> groups, List<Bookmark> bookmarks) {
        long groupId = 1, bookmarkId = 1;
        for (BookmarkGroup group : groups) {
            groupId = Math.max(groupId, group.getId() + 1);
        }
        for (Bookmark bookmark : bookmarks) {
            bookmarkId = Math.max(bookmarkId, bookmark.getId() + 1);
        }
        return new IdGenerator(groupId, bookmarkId);
    }
}
