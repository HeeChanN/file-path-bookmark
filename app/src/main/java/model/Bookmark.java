package model;

import service.bookmark.BookmarkRepository;

public class Bookmark {
    private long id;
    private long groupId;
    private String displayName;
    private String path; // .lnk 절대 경로 or 실제 경로
    private BookmarkType targetType;

    public Bookmark(long id, long groupId, String displayName, String path, BookmarkType targetType) {
        this.id = id;
        this.groupId = groupId;
        this.displayName = displayName;
        this.path = path;
        this.targetType = targetType;
    }

    public long getId() {
        return id;
    }

    public long getGroupId() {
        return groupId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPath() {
        return path;
    }

    public BookmarkType getTargetType() {
        return targetType;
    }

    public void update(String displayName, String path, BookmarkType targetType) {
        this.displayName = displayName;
        this.path = path;
        this.targetType = targetType;
    }
}
