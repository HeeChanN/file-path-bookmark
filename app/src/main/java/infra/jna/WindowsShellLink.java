package infra.jna;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WTypes;
import com.sun.jna.platform.win32.COM.util.Factory;
import com.sun.jna.platform.win32.COM.util.IUnknown;
import com.sun.jna.platform.win32.COM.util.annotation.*;


import java.nio.file.Files;
import java.nio.file.Path;

public class WindowsShellLink {
    // --- COM 인터페이스/클래스 정의 ---
    @ComInterface(iid="{000214F9-0000-0000-C000-000000000046}")
    interface IShellLinkW extends IUnknown {
        void SetPath(String pszFile);
        void SetWorkingDirectory(String pszDir);
        void SetDescription(String pszName);
        void SetIconLocation(String pszIconPath, int iIcon);
        void Resolve(com.sun.jna.platform.win32.WinDef.HWND hwnd, int fFlags);
        void GetPath(char[] pszFile, int cch, Pointer pfd, int fFlags);
    }

    @ComObject(clsId="{00021401-0000-0000-C000-000000000046}")
    interface ShellLink extends IShellLinkW {}

    @ComInterface(iid="{0000010B-0000-0000-C000-000000000046}")
    interface IPersistFile extends IUnknown {
        void Load(WTypes.LPWSTR pszFileName, int dwMode);
        void Save(WTypes.LPWSTR pszFileName, boolean fRemember);
    }

    // flags
    public static final int SLR_NO_UI  = 0x0001;
    public static final int SLR_UPDATE = 0x0004;
    public static final int SLGP_RAWPATH = 0x0004;
    private static final int MAX_PATH = 260;

    /** .lnk 생성 */
    public void createShortcut(Path target, Path lnkPath, String description) throws Exception {
        Factory f = new Factory();
        try {
            ShellLink link = f.createObject(ShellLink.class);
            link.SetPath(target.toString());
            if (description != null) link.SetDescription(description);
            link.SetWorkingDirectory(target.getParent() != null ? target.getParent().toString() : target.toString());
            IPersistFile pf = link.queryInterface(IPersistFile.class);
            Files.createDirectories(lnkPath.getParent());
            pf.Save(new WTypes.LPWSTR(lnkPath.toString()), true);
        } finally {
            f.disposeAll();
        }
    }

    /** .lnk → Resolve 후 현재 대상 경로 */
    public String resolve(Path lnkPath, boolean updateIfMoved, boolean noUI) throws Exception {
        Factory f = new Factory();
        try {
            ShellLink link = f.createObject(ShellLink.class);
            IPersistFile pf = link.queryInterface(IPersistFile.class);
            pf.Load(new WTypes.LPWSTR(lnkPath.toString()), 0);
            int flags = 0;
            if (updateIfMoved) flags |= SLR_UPDATE;
            if (noUI)          flags |= SLR_NO_UI;
            link.Resolve(null, flags);
            char[] buf = new char[MAX_PATH];
            link.GetPath(buf, buf.length, Pointer.NULL, SLGP_RAWPATH);
            return Native.toString(buf);
        } finally {
            f.disposeAll();
        }
    }

    /** 기존 링크의 타깃 갱신  -> 자동 추적 실패시 재지정*/
    public void updateTarget(Path lnkPath, Path newTarget) throws Exception {
        Factory f = new Factory();
        try {
            ShellLink link = f.createObject(ShellLink.class);
            IPersistFile pf = link.queryInterface(IPersistFile.class);
            pf.Load(new WTypes.LPWSTR(lnkPath.toString()), 0);
            link.SetPath(newTarget.toString());
            pf.Save(new WTypes.LPWSTR(lnkPath.toString()), true);
        } finally {
            f.disposeAll();
        }
    }
}

