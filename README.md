# 📘 Java 멀티 클라이언트 채팅 시스템
## 🇰🇷 채팅 프로그램 · 🇺🇸 Multi-Client Chat System

### Java를 기반으로 개발된 멀티 클라이언트 채팅 서버-클라이언트 시스템입니다.  
### 서버는 여러 클라이언트의 동시 접속을 처리하며, 콘솔 또는 GUI 인터페이스를 통해  
### 실시간 채팅을 수행할 수 있습니다.  
### This is a multi-client chat server-client system developed in Java.  
### The server handles multiple simultaneous client connections, and clients can  
### perform real-time chatting through console or GUI interfaces.

---

# ✨ 주요 기능 · Features

## 🇰🇷 한국어
- 멀티 스레드 기반 동시 접속 처리  
- 방(Room) 기반 채팅 시스템  
- 실시간 메시지 전송 및 수신  
- 귓속말(Whisper) 기능  
- 메시지 히스토리 관리  
- 하트비트(Heartbeat) 기반 연결 상태 관리  
- 사용자 및 방 관리 기능  
- GUI 클라이언트 제공 (Swing 기반)  
- 다국어 지원 (한국어/영어)  
- 테마 지원 (밝은 테마/어두운 테마)  
- 메시지 검색 및 북마크 기능  
- 채팅 기록 내보내기/가져오기  
- 이모지 입력 지원  

## 🇺🇸 English
- Multi-threaded concurrent connection handling  
- Room-based chat system  
- Real-time message sending and receiving  
- Whisper (private message) functionality  
- Message history management  
- Heartbeat-based connection state monitoring  
- User and room management features  
- GUI client provided (Swing-based)  
- Multi-language support (Korean/English)  
- Theme support (Light/Dark theme)  
- Message search and bookmark features  
- Chat history export/import  
- Emoji input support  

---

# 파일 구조 · File Structure

CK_Network_Final/
├── ChatServer.java                  ← 메인 서버 클래스<br/>
├── ClientSession.java               ← 클라이언트 세션 관리<br/>
├── MessageHistory.java              ← 메시지 히스토리 관리<br/>
├── ServerConfig.java                ← 서버 설정 관리<br/>
├── Logger.java                      ← 로깅 시스템<br/>
├── Frame.java                       ← 네트워크 프레임 프로토콜<br/>
├── MsgType.java                     ← 메시지 타입 정의<br/>
├── Kvp.java                         ← Key-Value 인코딩/디코딩<br/>
├── ChatClient.java                  ← 콘솔 기반 클라이언트<br/>
└──  ChatClientGUI.java               ← GUI 기반 클라이언트 (Swing)<br/>



---

# 사용 방법 · How to Use

## 🇰🇷 한국어

### 1) 서버 실행
명령 프롬프트 또는 터미널에서:
java ChatServer [포트번호]

예시:
java ChatServer 5555

포트 번호를 생략하면 기본값 5555를 사용합니다.

### 2) 콘솔 클라이언트 실행
명령 프롬프트 또는 터미널에서:
java ChatClient [호스트] [포트]

예시:
java ChatClient 127.0.0.1 5555

연결 후 닉네임을 입력하고 채팅을 시작할 수 있습니다.

주요 명령어:
/join <방이름>     - 방 입장<br/>
/leave             - 방 퇴장<br/>
/rooms             - 방 목록 조회<br/>
/users             - 사용자 목록 조회<br/>
/w <닉네임> <메시지> - 귓속말 전송<br/>
/history [방이름] [개수] - 채팅 히스토리 조회<br/>
/quit              - 연결 종료<br/>

### 3) GUI 클라이언트 실행
명령 프롬프트 또는 터미널에서:
java ChatClientGUI

GUI가 실행되면:
1. 호스트 주소 입력 (기본값: 127.0.0.1)
2. 포트 번호 입력 (기본값: 5555)
3. 닉네임 입력
4. "연결" 버튼 클릭

GUI 기능:
- 실시간 채팅 화면
- 방 목록 및 사용자 목록 표시
- 메뉴를 통한 다양한 설정 및 기능 접근
- 언어 변경 (한국어/영어)
- 테마 변경 (밝은 테마/어두운 테마)
- 폰트 크기 조절
- 메시지 검색 및 북마크
- 채팅 기록 내보내기/가져오기

### 4) 방 관리
- 방 생성: 메뉴 > 방 > 방 생성
- 방 삭제: 메뉴 > 방 > 방 삭제
- 방 입장: 방 이름 입력 후 "입장" 버튼 클릭
- 방 퇴장: "퇴장" 버튼 클릭

### 5) 사용자 관리
- 사용자 목록 조회: "사용자 새로고침" 버튼 클릭
- 사용자 정보 조회: 메뉴 > 사용자 > 프로필 설정
- 친구 추가/제거: 메뉴 > 사용자 > 친구 추가/제거
- 사용자 차단: 메뉴 > 사용자 > 사용자 차단

---

## 🇺🇸 English

### 1) Start Server
In command prompt or terminal:
java ChatServer [port]

Example:
java ChatServer 5555

If port number is omitted, default port 5555 is used.

### 2) Start Console Client
In command prompt or terminal:
java ChatClient [host] [port]

Example:
java ChatClient 127.0.0.1 5555

After connection, enter nickname and start chatting.

Main commands:
/join <room>       - Join a room<br/>
/leave             - Leave current room<br/>
/rooms             - List all rooms<br/>
/users             - List all users<br/>
/w <nick> <msg>    - Send whisper<br/>
/history [room] [count] - Get chat history<br/>
/quit              - Disconnect<br/>

### 3) Start GUI Client
In command prompt or terminal:
java ChatClientGUI

When GUI launches:
1. Enter host address (default: 127.0.0.1)
2. Enter port number (default: 5555)
3. Enter nickname
4. Click "Connect" button

GUI Features:
- Real-time chat display
- Room list and user list
- Various settings and features via menu
- Language change (Korean/English)
- Theme change (Light/Dark theme)
- Font size adjustment
- Message search and bookmark
- Chat history export/import

### 4) Room Management
- Create Room: Menu > Room > Create Room
- Delete Room: Menu > Room > Delete Room
- Join Room: Enter room name and click "Join" button
- Leave Room: Click "Leave" button

### 5) User Management
- View User List: Click "Refresh Users" button
- View User Info: Menu > User > Set Profile
- Add/Remove Friends: Menu > User > Add/Remove Friend
- Block User: Menu > User > Block User

---

# 기술적 특징 · Technical Features

## 🇰🇷 한국어

### 스레드 동기화 기법
- ConcurrentHashMap: 세션, 방, 히스토리 관리
- AtomicInteger/AtomicLong: 원자적 연산
- BlockingQueue: 생산자-소비자 패턴
- synchronized: 출력 스트림 동기화
- ExecutorService: 스레드 풀 관리
- volatile: 변수 가시성 보장

### 네트워크 프로토콜
- 바이너리 프레임 기반 프로토콜
- 매직 넘버를 통한 프로토콜 식별
- 시퀀스 번호를 통한 메시지 순서 보장
- 하트비트를 통한 연결 상태 모니터링

### 아키텍처
- 클라이언트-서버 아키텍처
- 각 클라이언트마다 별도의 Reader/Writer 스레드
- 방 기반 메시지 브로드캐스팅
- 메시지 히스토리 관리

## 🇺🇸 English

### Thread Synchronization Techniques
- ConcurrentHashMap: Session, room, history management
- AtomicInteger/AtomicLong: Atomic operations
- BlockingQueue: Producer-consumer pattern
- synchronized: Output stream synchronization
- ExecutorService: Thread pool management
- volatile: Variable visibility guarantee

### Network Protocol
- Binary frame-based protocol
- Protocol identification via magic number
- Message ordering via sequence numbers
- Connection state monitoring via heartbeat

### Architecture
- Client-server architecture
- Separate Reader/Writer threads per client
- Room-based message broadcasting
- Message history management

---

# ⚠ 주의 사항 · Notes

## 🇰🇷 한국어
- 서버는 기본 포트 5555를 사용합니다  
- 최대 동시 연결 수는 기본값 1000입니다 (ServerConfig에서 변경 가능)  
- 방당 최대 인원은 기본값 100명입니다  
- 메시지 히스토리는 방별로 최대 100개까지 저장됩니다  
- GUI 클라이언트는 Swing을 사용하므로 Java 8 이상이 필요합니다  
- 서버를 먼저 실행한 후 클라이언트를 연결해야 합니다  
- 같은 닉네임으로 중복 로그인할 수 없습니다  
- 하트비트 간격은 기본값 30초이며, 3배 시간 동안 응답이 없으면 연결이 끊깁니다  

## 🇺🇸 English
- Server uses default port 5555  
- Maximum concurrent connections is 1000 by default (configurable in ServerConfig)  
- Maximum users per room is 100 by default  
- Message history stores up to 100 messages per room  
- GUI client uses Swing, requiring Java 8 or higher  
- Server must be started before connecting clients  
- Duplicate login with the same nickname is not allowed  
- Heartbeat interval is 30 seconds by default, connection times out after 3x interval with no response  

---

# 수업 내용 적용 · Course Content Application

## 🇰🇷 한국어

본 프로그램은 네트워크 프로그래밍 수업에서 배운 내용을 실제로 적용하여 개발되었습니다:

1. 소켓 프로그래밍
   - ServerSocket과 Socket을 사용한 클라이언트-서버 통신
   - TCP 소켓을 통한 신뢰성 있는 데이터 전송

2. 스레드 프로그래밍
   - 멀티 스레드 환경에서의 동시성 제어
   - 스레드 풀을 통한 효율적인 스레드 관리

3. 동기화 기법
   - synchronized 블록을 통한 임계 영역 보호
   - 동시성 컬렉션 사용
   - 원자적 변수를 통한 원자적 연산
   - BlockingQueue를 통한 스레드 간 통신

4. 네트워크 프로토콜 설계
   - 바이너리 프로토콜 설계 및 구현
   - 프레임 기반 메시지 구조
   - 하트비트를 통한 연결 상태 모니터링

## 🇺🇸 English

This program was developed by applying the content learned in the network programming course:

1. Socket Programming
   - Client-server communication using ServerSocket and Socket
   - Reliable data transmission via TCP sockets

2. Thread Programming
   - Concurrency control in multi-threaded environments
   - Efficient thread management through thread pools

3. Synchronization Techniques
   - Critical section protection via synchronized blocks
   - Use of concurrent collections
   - Atomic operations via atomic variables
   - Inter-thread communication via BlockingQueue

4. Network Protocol Design
   - Binary protocol design and implementation
   - Frame-based message structure
   - Connection state monitoring via heartbeat


---

# 개발 환경 · Development Environment

- 언어: Java
- 최소 Java 버전: Java 8 이상
- GUI 라이브러리: Swing
- 개발 도구: 텍스트 에디터 또는 IDE (IntelliJ IDEA, Eclipse 등)

---
<img width="1917" height="1035" alt="image" src="https://github.com/user-attachments/assets/9e17e3a0-2d49-4ad1-a7e0-e833f59a0a9e" />

