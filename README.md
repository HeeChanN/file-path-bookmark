# 파일 경로 북마크 (File Path Bookmark)

> 웹 브라우저에서 파일 업로드 시 자주 사용하는 경로를 빠르게 찾아주는 데스크톱 애플리케이션

![Java](https://img.shields.io/badge/Java-17-007396?style=flat-square&logo=java)
![Swing](https://img.shields.io/badge/Swing-UI-orange?style=flat-square)
![Chrome Extension](https://img.shields.io/badge/Chrome-Extension-4285F4?style=flat-square&logo=googlechrome)

## 프로젝트 소개

Chrome 브라우저에서 파일 업로드 버튼을 클릭할 때마다 복잡한 폴더 구조를 탐색하는 불편함을 해결하기 위해 만든 애플리케이션입니다.  
자주 사용하는 파일과 폴더 경로를 북마크로 저장하고, 파일 업로드 시 원클릭으로 해당 위치를 불러올 수 있습니다.

<img width="1809" height="1184" alt="image" src="https://github.com/user-attachments/assets/158cd895-d8aa-47f5-9fcf-320e71c04601" />


### 주요 기능

- **파일/폴더 경로 북마크 관리**: 자주 사용하는 경로를 그룹별로 분류하여 저장
- **브라우저 연동**: Chrome에서 파일 업로드 버튼 클릭 시 자동으로 북마크 창 표시
- **원클릭 경로 복사**: 저장된 경로를 클릭 한 번으로 파일 탐색기에 적용
- **경량 저장소**: 별도의 데이터베이스 없이 파일 기반 영속성 구현

## 기술 스택

- **Backend**: Java 17
- **UI**: Swing
- **Storage**: MicroStream (파일 기반 객체 영속화)
- **Browser Integration**: Chrome Extension (JavaScript)

## 데모

https://github.com/user-attachments/assets/02255001-1335-4958-b0ff-34ad9061da42


파일 업로드 버튼 클릭 → 북마크 창 자동 표시 → 경로 선택 → 복사 및 즉시 적용

## 아키텍처

```
┌─────────────────┐         Native Messaging         ┌──────────────────┐
│  Chrome Browser │ ←──────────────────────────────→ │  Java Desktop    │
│   (Extension)   │                                  │   Application    │
└─────────────────┘                                  └──────────────────┘
        ↓                                                     ↓
   파일 업로드 감지                                      북마크 관리
   (input[type=file])                                   (Swing UI)
                                                              ↓
                                                    ┌──────────────────┐
                                                    │   MicroStream    │
                                                    │  (File Storage)  │
                                                    └──────────────────┘
```

### 핵심 설계 특징

- **단일 책임 원칙(SRP)**: 도메인 로직과 저장 로직을 서비스 계층으로 분리
- **어댑터 패턴**: 저장소 인터페이스를 추상화하여 향후 저장소 교체 시 유연성 확보
- **단순한 도메인 모델**: Group과 Bookmark 두 가지 핵심 객체로 구성

## 시작하기

### 사전 요구사항

- Java 17 이상
- Chrome 브라우저

### 설치 방법

1. **레포지토리 클론**
```bash
git clone https://github.com/your-username/file-path-bookmark.git
cd file-path-bookmark
```

2. **애플리케이션 빌드**
```bash
cd app/
./gradlew clean shadowJar
```

3. **Chrome 확장 프로그램 설치**
   - Chrome에서 `chrome://extensions/` 접속
   - "개발자 모드" 활성화
   - "압축해제된 확장 프로그램을 로드합니다" 클릭
   - `chrome_extension` 폴더 선택

4. **Native Messaging Host 등록**
```bash
# Windows
New-Item -Path "HKCU:\Software\Google\Chrome\NativeMessagingHosts\com.filepathbookmark.host" -Force | Out-Null
New-ItemProperty `
  -Path "HKCU:\Software\Google\Chrome\NativeMessagingHosts\com.filepathbookmark.host" `
  -Name "(default)" -PropertyType String `
  -Value "C:\<clone 위치>\message_host\com.filepathbookmark.host.json" -Force | Out-Null
```

5. **실행가능한 애플리케이션 만들기 (.exe 파일 생성)**
```bash
jpackage `
  --type app-image `
  --name FilePickerHelper `
  --input "build/libs" `
  --main-class App `
  --main-jar "file-path-bookmark-0.1.0-SNAPSHOT-all.jar" `
  --dest "C:\<clone 위치>\app"
```

6. **exe 실행**

우하단 숨겨진 아이콘 확인인

## 사용 방법

1. **북마크 추가**: 애플리케이션에서 `+` 버튼 클릭 → 파일/폴더 선택
2. **그룹 관리**: 용도별로 북마크를 그룹으로 분류
3. **브라우저에서 사용**: 
   - 웹페이지에서 파일 업로드 버튼 클릭
   - 자동으로 표시되는 북마크 창에서 원하는 경로 선택
   - 파일 탐색기에서 해당 위치로 즉시 이동



## 향후 계획

- 검색 기능 추가
- 바로가기(.lnk) 지원으로 경로 변경 대응
- 사용성 개선 (설치 과정 간소화, 크롬 Web Store 등록)
- UI/UX 개선


## 개발 회고

이 프로젝트를 통해 배운 점:
- 객체지향 설계 원칙(SRP)과 디자인 패턴(어댑터) 적용
- Chrome Extension과 Native Messaging API를 활용한 브라우저 연동
- AI를 활용한 개발 효율화 전략
- 사용자 경험 중심의 문제 해결

자세한 개발 과정은 [블로그 포스트](https://hechan2.tistory.com/29)를 참고해주세요.

