package com.mz.jarboot.common;

/**
 * We defined a response data structure or protocol used give back the executed result.
 * @author majianzheng
 */
public class CommandResponse implements CmdProtocol {
    private static final char SUCCESS_FLAG = '1';
    private Boolean success;
    private ResponseType responseType = ResponseType.UNKNOWN;
    private String body;
    private String sessionId;

    /**
     * Convert to the raw data used network transmission.
     * @return The data which to send by network.
     */
    @Override
    public String toRaw() {
        //格式: 前3个字符是控制位 + 数据体
        //控制位 0 响应类型、1 是否成功、2保留填-
        char rt = this.getResponseTypeChar();
        return new StringBuilder()
                //响应类型
                .append(rt)
                //是否成功标志
                .append(Boolean.TRUE.equals(success) ? SUCCESS_FLAG : '0')
                //保留位
                .append('-')
                .append(body)
                .append(' ')
                //最后填充sessionId
                .append(sessionId)
                .toString();
    }
    @Override
    public void fromRaw(String raw) {
        this.success = false;
        //反向解析出类实例
        //获取响应类型
        switch (raw.charAt(0)) {
            case CommandConst.ACK_TYPE:
                this.setResponseType(ResponseType.ACK);
                break;
            case CommandConst.ONLINE_TYPE:
                this.setResponseType(ResponseType.ONLINE);
                break;
            case CommandConst.CONSOLE_TYPE:
                this.setResponseType(ResponseType.CONSOLE);
                break;
            case CommandConst.JSON_RESULT_TYPE:
                this.setResponseType(ResponseType.JSON_RESULT);
                break;
            case CommandConst.CMD_END_TYPE:
                this.setResponseType(ResponseType.COMMAND_END);
                break;
            default:
                this.setResponseType(ResponseType.UNKNOWN);
                break;
        }

        this.success = SUCCESS_FLAG == raw.charAt(1);

        int l = raw.lastIndexOf(' ');
        if (-1 == l) {
            this.success = false;
            this.body = "协议错误，未发现sessionId";
            return;
        }
        this.body = raw.substring(3, l);
        this.sessionId = raw.substring(l + 1);
    }

    public static CommandResponse createFromRaw(String raw) {
        CommandResponse response = new CommandResponse();
        response.fromRaw(raw);
        return response;
    }
    private char getResponseTypeChar() {
        switch (responseType) {
            case ACK:
                return CommandConst.ACK_TYPE;
            case ONLINE:
                return CommandConst.ONLINE_TYPE;
            case CONSOLE:
                return CommandConst.CONSOLE_TYPE;
            case JSON_RESULT:
                return CommandConst.JSON_RESULT_TYPE;
            case COMMAND_END:
                return CommandConst.CMD_END_TYPE;
            default:
                break;
        }
        return '-';
    }

    public ResponseType getResponseType() {
        return responseType;
    }

    public void setResponseType(ResponseType responseType) {
        this.responseType = responseType;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
