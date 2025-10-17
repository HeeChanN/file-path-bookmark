package service.bookmark_group;

import model.BookmarkGroup;

import java.util.List;
import java.util.Optional;

public interface BookmarkGroupRepository {
    BookmarkGroup save(BookmarkGroup bookmarkGroup);
    Optional<BookmarkGroup> findById(long id);
    BookmarkGroup update(BookmarkGroup bookmarkGroup);
    List<BookmarkGroup> findAll();
    void deleteById(long id);
    void saveAll(List<BookmarkGroup> bookmarkGroups);
}
