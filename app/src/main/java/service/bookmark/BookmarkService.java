package service.bookmark;

import infra.FileUtils;
import model.Bookmark;
import model.BookmarkType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.IdGenerator;

import java.util.List;


public class BookmarkService {
    private final BookmarkRepository bookmarkRepository;
    private final IdGenerator idGenerator;
    private final Logger logger = LoggerFactory.getLogger(BookmarkService.class);

    public BookmarkService(BookmarkRepository bookmarkRepository, IdGenerator idGenerator) {
        this.bookmarkRepository = bookmarkRepository;
        this.idGenerator = idGenerator;
    }

    public Bookmark createBookmark(long groupId, String displayName, String path) {
        BookmarkType bookmarkType = FileUtils.validateFileOrDirectory(path);
        Bookmark bookmark = bookmarkRepository.save(new Bookmark(idGenerator.nextBookmarkId(),groupId,displayName,path, bookmarkType));
        logger.info("createBookmark() - {}",bookmarkRepository.findById(bookmark.getId()));
        return bookmark;
    }

    public void reorderBookmark(long groupId, long prevId, int toIndex) {
        List<Bookmark> bookmarks = bookmarkRepository.findAllByGroupId(groupId);

        int fromIndex = -1;
        for (int i = 0; i < bookmarks.size(); i++) {
            if (bookmarks.get(i).getId() == prevId) {
                fromIndex = i;
                break;
            }
        }
        if (fromIndex < 0) {
            throw new RuntimeException("unknown bookMarkId: " + prevId);
        }
        Bookmark bookmark = bookmarks.remove(fromIndex);
        bookmarks.add(toIndex,bookmark);
        bookmarkRepository.saveAll(bookmarks);
    }

    public Bookmark updateBookmark(long bookmarkId, String displayName, String path) {
        Bookmark bookmark = bookmarkRepository.findById(bookmarkId)
                .orElseThrow(() -> new RuntimeException("bookmark not found"));
        BookmarkType bookmarkType = FileUtils.validateFileOrDirectory(path);
        bookmark.update(displayName,path, bookmarkType);
        return bookmarkRepository.update(bookmark);
    }

    public void remove(long id){
        bookmarkRepository.deleteById(id);
    }
}
