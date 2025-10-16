package service.bookmark;

import model.Bookmark;
import model.BookmarkGroup;

import java.util.Optional;

public interface BookmarkRepository {
    Bookmark save(Bookmark bookmark);
    void deleteById(long id);
    Optional<Bookmark> findById(long id);
//    Bookmark update(Bookmark bookmark);
}
