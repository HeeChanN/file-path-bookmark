package service.bookmark;

import model.Bookmark;
import model.BookmarkTargetType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.IdGenerator;
import service.bookmark_group.BookmarkGroupService;


public class BookmarkService {
    private final BookmarkRepository bookmarkRepository;
    private final IdGenerator idGenerator;
    private final Logger logger = LoggerFactory.getLogger(BookmarkService.class);

    public BookmarkService(BookmarkRepository bookmarkRepository, IdGenerator idGenerator) {
        this.bookmarkRepository = bookmarkRepository;
        this.idGenerator = idGenerator;
    }

    public Bookmark createBookmark(long groupId, String displayName, String lnkPath) {
        logger.info("createBookmark() - {}, {}, {}",groupId,displayName,lnkPath);
        Bookmark bookmark = bookmarkRepository.save(new Bookmark(idGenerator.nextBookmarkId(),groupId,displayName,lnkPath, BookmarkTargetType.FILE));
        logger.info("createBookmark() - {}",bookmarkRepository.findById(bookmark.getId()));
        return bookmark;
    }

    public void remove(long id){
        bookmarkRepository.deleteById(id);
    }

    public void reorderCategory() {
        // TODO: 기본 로직 모두 구현하고 작성하기
    }
}
