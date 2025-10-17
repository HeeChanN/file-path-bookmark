package service.bookmark_group;

import model.BookmarkGroup;
import service.IdGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BookmarkGroupService {

    private final BookmarkGroupRepository bookmarkGroupRepository;
    private final IdGenerator idGenerator;

    public BookmarkGroupService(BookmarkGroupRepository bookmarkGroupRepository, IdGenerator idGenerator) {
        this.bookmarkGroupRepository = bookmarkGroupRepository;
        this.idGenerator = idGenerator;
    }

    public BookmarkGroup createBookmarkGroup(String name) {
        return bookmarkGroupRepository.save(new BookmarkGroup(name, idGenerator.nextGroupId()));
    }

    public BookmarkGroup getBookmarkGroup(long id){
        return bookmarkGroupRepository.findById(id).orElseThrow(
                () -> new RuntimeException("그룹이 존재하지 않습니다.")
        );
    }

    public List<BookmarkGroup> getBookmarkGroups(){
        return bookmarkGroupRepository.findAll();
    }

    public BookmarkGroup renameBookmarkGroup(long id, String name) {
        BookmarkGroup bookmarkGroup = bookmarkGroupRepository.findById(id).orElseThrow(
                () -> new RuntimeException("해당 그룹이 존재하지 않습니다.")
        );
        bookmarkGroup.rename(name);
        return bookmarkGroupRepository.update(bookmarkGroup);
    }

    public void deleteBookmarkGroup(long id){
        checkDefaultGroup(id);
        bookmarkGroupRepository.deleteById(id);
    }


    /**
     * TODO: 리스트
     * 새 순서가 null 아님 / 비어있지 않을 경우
     * 크기 일치: newOrder.size == 기존개수
     * 동일한 집합: 빠진/낯선 ID 없음
     * 중복 없음
     * 인덱스 유효 범위
     * No-op 감지: 순서가 동일하면 저장 생략(불필요 I/O 방지)
     * */
    public void reorderBookmarkGroups(long prevId, int toIndex){
        List<BookmarkGroup> bookmarkGroups = bookmarkGroupRepository.findAll();

        int fromIndex = -1;
        for (int i = 0; i < bookmarkGroups.size(); i++) {
            if (bookmarkGroups.get(i).getId() == prevId) {
                fromIndex = i;
                break;
            }
        }
        if (fromIndex < 0) {
            throw new RuntimeException("unknown groupId: " + prevId);
        }
        BookmarkGroup bookmarkGroup = bookmarkGroups.remove(fromIndex);
        bookmarkGroups.add(toIndex,bookmarkGroup);
        bookmarkGroupRepository.saveAll(bookmarkGroups);
    }

    private void checkDefaultGroup(long id){
        if(id == 1){
            throw new RuntimeException("기본 그룹은 수정/삭제 할 수 없습니다.");
        }
    }
}
