package model;

public enum BookmarkTargetType {
    FILE("파일"),
    FOLDER("폴더"),
    ;

    BookmarkTargetType(String name) {
        this.name = name;
    }

    private String name;

    public String getName() {
        return name;
    }
}
