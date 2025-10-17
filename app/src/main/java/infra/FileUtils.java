package infra;

import model.BookmarkType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

public class FileUtils {

    public static BookmarkType validateFileOrDirectory(String path){
        Path p =  Paths.get(path);
        try {
            if (!Files.exists(p, NOFOLLOW_LINKS)) {
                throw new RuntimeException("경로가 존재하지 않습니다: " + p);
            }
            BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class, NOFOLLOW_LINKS);
            if (a.isDirectory()) {
                return BookmarkType.DIRECTORY;
            }
            if (a.isRegularFile()) {
                // (선택) Windows .lnk 거부하려면 확장자 체크 추가
                // if (isWindows() && p.toString().toLowerCase().endsWith(".lnk"))
                //     throw new UnsupportedPathTypeException("Windows 바로가기는 지원하지 않습니다: " + p);
                return BookmarkType.FILE;
            }
            throw new RuntimeException("특수 파일은 지원하지 않습니다: " + p);
        }
        catch (Exception e) {
            throw new RuntimeException("경로 확인 오류: "+e.getMessage());
        }
    }
}
