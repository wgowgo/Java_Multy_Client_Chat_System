
public final class MsgType {
    private MsgType() {}

  
    public static final byte HELLO      = 0x01;
    public static final byte WELCOME    = 0x02;

   
    public static final byte LOGIN      = 0x03;
    public static final byte LOGIN_OK   = 0x04;
    public static final byte LOGIN_FAIL = 0x05;
    public static final byte LOGOUT     = 0x06;

   
    public static final byte JOIN       = 0x10;
    public static final byte LEAVE      = 0x11;
    public static final byte JOIN_OK    = 0x14;
    public static final byte LEAVE_OK   = 0x15;
    public static final byte ROOM_LIST  = 0x16;
    public static final byte ROOM_LIST_RESP = 0x17;
    public static final byte ROOM_INFO  = 0x18;
    public static final byte ROOM_INFO_RESP = 0x19;

  
    public static final byte CHAT       = 0x12;
    public static final byte WHISPER    = 0x13;
    public static final byte CHAT_HISTORY = 0x1A;
    public static final byte CHAT_HISTORY_RESP = 0x1B;

   
    public static final byte USER_LIST  = 0x30;
    public static final byte USER_LIST_RESP = 0x31;
    public static final byte USER_INFO  = 0x32;
    public static final byte USER_INFO_RESP = 0x33;
    public static final byte USER_STATUS = 0x34;
    public static final byte USER_PROFILE = 0x35;
    public static final byte USER_PROFILE_RESP = 0x36;
    public static final byte USER_BLOCK = 0x37;
    public static final byte USER_UNBLOCK = 0x38;
    public static final byte FRIEND_ADD = 0x39;
    public static final byte FRIEND_REMOVE = 0x3A;
    public static final byte FRIEND_LIST = 0x3B;
    public static final byte FRIEND_LIST_RESP = 0x3C;

    public static final byte PING       = 0x20;
    public static final byte PONG       = 0x21;

    public static final byte FILE_REQ   = 0x40;
    public static final byte FILE_REQ_RESP = 0x41;
    public static final byte FILE_DATA  = 0x42;

    public static final byte MSG_EDIT = 0x50;
    public static final byte MSG_DELETE = 0x51;
    public static final byte MSG_PIN = 0x52;
    public static final byte MSG_UNPIN = 0x53;
    public static final byte MSG_BOOKMARK = 0x54;
    public static final byte MSG_SEARCH = 0x55;
    public static final byte MSG_SEARCH_RESP = 0x56;
    public static final byte MSG_QUOTE = 0x57;

    public static final byte ROOM_CREATE = 0x60;
    public static final byte ROOM_DELETE = 0x61;
    public static final byte ROOM_SET_PASSWORD = 0x62;
    public static final byte ROOM_SET_DESCRIPTION = 0x63;
    public static final byte ROOM_SET_ADMIN = 0x64;
    public static final byte ROOM_INVITE = 0x65;
    public static final byte ROOM_SET_LIMIT = 0x66;
    public static final byte ROOM_ARCHIVE = 0x67;

    public static final byte STATS_REQUEST = 0x70;
    public static final byte STATS_RESP = 0x71;
    public static final byte STATS_USER_ACTIVITY = 0x72;
    public static final byte STATS_ROOM_ACTIVITY = 0x73;
    public static final byte STATS_WORD_FREQ = 0x74;

    public static final byte ERROR      = 0x7F;

    public static String name(byte t) {
        return switch (t) {
            case HELLO -> "HELLO";
            case WELCOME -> "WELCOME";
            case LOGIN -> "LOGIN";
            case LOGIN_OK -> "LOGIN_OK";
            case LOGIN_FAIL -> "LOGIN_FAIL";
            case LOGOUT -> "LOGOUT";
            case JOIN -> "JOIN";
            case LEAVE -> "LEAVE";
            case JOIN_OK -> "JOIN_OK";
            case LEAVE_OK -> "LEAVE_OK";
            case ROOM_LIST -> "ROOM_LIST";
            case ROOM_LIST_RESP -> "ROOM_LIST_RESP";
            case ROOM_INFO -> "ROOM_INFO";
            case ROOM_INFO_RESP -> "ROOM_INFO_RESP";
            case CHAT -> "CHAT";
            case WHISPER -> "WHISPER";
            case CHAT_HISTORY -> "CHAT_HISTORY";
            case CHAT_HISTORY_RESP -> "CHAT_HISTORY_RESP";
            case USER_LIST -> "USER_LIST";
            case USER_LIST_RESP -> "USER_LIST_RESP";
            case USER_INFO -> "USER_INFO";
            case USER_INFO_RESP -> "USER_INFO_RESP";
            case USER_STATUS -> "USER_STATUS";
            case USER_PROFILE -> "USER_PROFILE";
            case USER_PROFILE_RESP -> "USER_PROFILE_RESP";
            case USER_BLOCK -> "USER_BLOCK";
            case USER_UNBLOCK -> "USER_UNBLOCK";
            case FRIEND_ADD -> "FRIEND_ADD";
            case FRIEND_REMOVE -> "FRIEND_REMOVE";
            case FRIEND_LIST -> "FRIEND_LIST";
            case FRIEND_LIST_RESP -> "FRIEND_LIST_RESP";
            case PING -> "PING";
            case PONG -> "PONG";
            case FILE_REQ -> "FILE_REQ";
            case FILE_REQ_RESP -> "FILE_REQ_RESP";
            case FILE_DATA -> "FILE_DATA";
            case MSG_EDIT -> "MSG_EDIT";
            case MSG_DELETE -> "MSG_DELETE";
            case MSG_PIN -> "MSG_PIN";
            case MSG_UNPIN -> "MSG_UNPIN";
            case MSG_BOOKMARK -> "MSG_BOOKMARK";
            case MSG_SEARCH -> "MSG_SEARCH";
            case MSG_SEARCH_RESP -> "MSG_SEARCH_RESP";
            case MSG_QUOTE -> "MSG_QUOTE";
            case ROOM_CREATE -> "ROOM_CREATE";
            case ROOM_DELETE -> "ROOM_DELETE";
            case ROOM_SET_PASSWORD -> "ROOM_SET_PASSWORD";
            case ROOM_SET_DESCRIPTION -> "ROOM_SET_DESCRIPTION";
            case ROOM_SET_ADMIN -> "ROOM_SET_ADMIN";
            case ROOM_INVITE -> "ROOM_INVITE";
            case ROOM_SET_LIMIT -> "ROOM_SET_LIMIT";
            case ROOM_ARCHIVE -> "ROOM_ARCHIVE";
            case STATS_REQUEST -> "STATS_REQUEST";
            case STATS_RESP -> "STATS_RESP";
            case STATS_USER_ACTIVITY -> "STATS_USER_ACTIVITY";
            case STATS_ROOM_ACTIVITY -> "STATS_ROOM_ACTIVITY";
            case STATS_WORD_FREQ -> "STATS_WORD_FREQ";
            case ERROR -> "ERROR";
            default -> "UNKNOWN(" + t + ")";
        };
    }
}
