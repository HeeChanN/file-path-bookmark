package persistence;

import model.BookmarkGroup;
import one.microstream.storage.types.StorageManager;
import service.bookmark_group.BookmarkGroupRepository;
import one.microstream.storage.embedded.types.EmbeddedStorageManager;

import java.util.List;
import java.util.Optional;

public class BookmarkGroupMicroStreamRepository implements BookmarkGroupRepository {

    private final RootData root;
    private final StorageManager storage;

    public BookmarkGroupMicroStreamRepository(RootData root, EmbeddedStorageManager storage) {
        this.root = root;
        this.storage = storage;
    }

    @Override
    public BookmarkGroup save(BookmarkGroup bookmarkGroup) {
        root.groups().add(bookmarkGroup);
        storage.store(root.groups());
        //persistExec.execute(() -> storage.store(root.groups()));
        return bookmarkGroup;
    }

    @Override
    public Optional<BookmarkGroup> findById(long id) {
        return root.groups().stream()
                .filter(group -> group.getId() == id)
                .findFirst();
    }

    @Override
    public BookmarkGroup update(BookmarkGroup bookmarkGroup) {
        storage.store(bookmarkGroup);
        //persistExec.execute(() -> storage.store(g));
        return bookmarkGroup;
    }

    @Override
    public List<BookmarkGroup> findAll() {
        return root.groups();
    }

    @Override
    public void deleteById(long id) {
        boolean removed = root.groups().removeIf(bookmarkGroup -> bookmarkGroup.getId() == id);

        if (!removed) {
            throw new RuntimeException("BookmarkGroup not found: " + id);
        }
        storage.store(root.groups());
    }

    public void saveAll(List<BookmarkGroup> bookmarkGroups) {
        storage.store(bookmarkGroups);
    }
}
