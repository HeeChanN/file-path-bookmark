package model;

import java.util.UUID;

public class Bookmark {
    private long id;
    private long groupId;
    private String displayName;
    private String linkPath; // .lnk 절대 경로 or 실제 경로
    private BookmarkTargetType targetType;

    public Bookmark(long id, long groupId, String displayName, String linkPath, BookmarkTargetType targetType) {
        this.id = id;
        this.groupId = groupId;
        this.displayName = displayName;
        this.linkPath = linkPath;
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

    public String getLinkPath() {
        return linkPath;
    }
}
