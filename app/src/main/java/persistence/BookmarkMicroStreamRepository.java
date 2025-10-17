package persistence;

import model.Bookmark;
import model.BookmarkGroup;
import one.microstream.storage.types.StorageManager;
import service.bookmark.BookmarkRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class BookmarkMicroStreamRepository implements BookmarkRepository {

    private final RootData root;
    private final StorageManager storage;

    public BookmarkMicroStreamRepository(RootData root, StorageManager storage) {
        this.root = root;
        this.storage = storage;
    }

    @Override
    public Bookmark save(Bookmark bookmark) {
        BookmarkGroup bookmarkGroup = root.groups().stream()
                .filter(group -> group.getId() == bookmark.getGroupId())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + bookmark.getGroupId()));

        bookmarkGroup.include(bookmark);                                   // 그룹 내부 리스트 변경
        storage.store(bookmarkGroup.getBookmarks());
        //persistExec.execute(() -> storage.store(root.bookmarks()));
        return bookmark;
    }

    @Override
    public void deleteById(long id) {
        boolean removed = root.groups().stream()
                .anyMatch(group -> group.exclude(id));

        if (!removed) {
            throw new RuntimeException("Bookmark not found: " + id);
        }
        storage.store(root.groups());
    }

    @Override
    public Optional<Bookmark> findById(long id) {
        return root.groups().stream()
                .flatMap(group -> group.getBookmarks().stream())
                .filter(bookmark -> bookmark.getId() == id)
                .findFirst();
    }

    @Override
    public Bookmark update(Bookmark bookmark) {
        BookmarkGroup bookmarkGroup = root.groups().stream()
                .filter(group -> group.getId() == bookmark.getGroupId())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + bookmark.getGroupId()));
        storage.store(bookmarkGroup.getBookmarks());
        return bookmark;
    }

    @Override
    public List<Bookmark> findAllByGroupId(long groupId) {
        BookmarkGroup group = root.groups().stream()
                .filter(g -> java.util.Objects.equals(g.getId(), groupId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("그룹이 존재하지 않습니다."));
        return group.getBookmarks();
    }

    @Override
    public void saveAll(BookmarkGroup bookmarkGroup) {
        storage.store(bookmarkGroup.getBookmarks());
    }
}
