import Logger from "@/common/Logger";
import StringUtil from "@/common/StringUtil";
import {MSG_EVENT} from "@/common/EventConst";
import {JarBootConst} from "@/common/JarBootConst";
import CommonNotice from "@/common/CommonNotice";
import { message } from 'antd';

interface MsgData {
    event: number,
    server: string,
    body: any
}
let msg: any = null;
class WsManager {
    private static websocket: any = null;
    private static fd: any = null;
    private static _messageHandler = new Map<number, (data: MsgData) => void>();

    public static addMessageHandler(key: number, handler: (data: MsgData) => void) {
        if (handler && !WsManager._messageHandler.has(key)) {
            WsManager._messageHandler.set(key, handler);
        }
    }

    public static clearHandlers() {
        WsManager._messageHandler.clear();
    }

    public static removeMessageHandler(key: number) {
        if (key) {
            WsManager._messageHandler.delete(key);
        }
    }

    public static sendMessage(text: string) {
        if (WsManager.websocket && WebSocket.OPEN === WsManager.websocket.readyState) {
            WsManager.websocket.send(text);
            return;
        }
        WsManager.initWebsocket();
    }

    public static initWebsocket() {
        WsManager.addMessageHandler(MSG_EVENT.NOTICE_INFO, WsManager._noticeInfo);
        WsManager.addMessageHandler(MSG_EVENT.NOTICE_WARN, WsManager._noticeWarn);
        WsManager.addMessageHandler(MSG_EVENT.NOTICE_ERROR, WsManager._noticeError);
        if (WsManager.websocket) {
            if (WebSocket.OPEN === WsManager.websocket.readyState ||
                WebSocket.CONNECTING === WsManager.websocket.readyState) {
                return;
            }
        }
        let url = process.env.NODE_ENV === 'development' ?
            `ws://${window.location.hostname}:9899/public/jarboot-service/ws` :
            `ws://${window.location.host}/public/jarboot-service/ws`;
        WsManager.websocket = new WebSocket(url);
        WsManager.websocket.onmessage = WsManager._onMessage;
        WsManager.websocket.onopen = WsManager._onOpen;
        WsManager.websocket.onclose = WsManager._onClose;
        WsManager.websocket.onerror = WsManager._onError;
    }

    private static _noticeInfo = (data: MsgData) => {
        CommonNotice.info("提示", data.body);
    };
    private static _noticeWarn = (data: MsgData) => {
        CommonNotice.warn("警告", data.body);
    };
    private static _noticeError = (data: MsgData) => {
        CommonNotice.error("错误", data.body);
    };

    private static _onMessage = (e: any) => {
        if (!StringUtil.isString(e?.data)) {
            //二进制数据
            return;
        }
        const resp: string = e.data;
        try {
            let i = resp.indexOf(JarBootConst.PROTOCOL_SPLIT);
            if (-1 === i) {
                Logger.warn(`协议错误，${resp}`);
                return;
            }
            const server = 0 === i ? '' : resp.substring(0, i);
            let k = resp.indexOf(JarBootConst.PROTOCOL_SPLIT, i + 1);
            if (-1 === k) {
                Logger.warn(`协议错误，获取事件类型失败，${resp}`);
                return;
            }
            const event = parseInt(resp.substring(i + 1, k));
            const body = resp.substring(k + 1);
            let data: MsgData = {event, body, server};
            const handler = WsManager._messageHandler.get(data.event);
            handler && handler(data);
        } catch (error) {
            Logger.warn(error);
        }
    };

    private static _onOpen = () => {
        Logger.log("连接Websocket服务器成功！");
        msg && msg();
        msg = null;
        if (null !== WsManager.fd) {
            //连接成功，取消重连机制
            clearInterval(WsManager.fd);
            WsManager.fd = null;
        }
    };

    private static _onClose = () => {
        Logger.log("websocket连接关闭！");
        WsManager._reconnect();
    };

    private static _onError = (e: Error) => {
        Logger.log("websocket异常关闭！");
        Logger.error(e);
        WsManager._reconnect();
    };

    private static _reconnect() {
        if (null !== WsManager.fd) {
            return;
        }
        msg = message.loading('reconnecting...', 0);
        WsManager.fd = setInterval(() => {
            if (null === WsManager.fd) {
                //已经进入连onOpen
                return;
            }
            if (WebSocket.CONNECTING === WsManager.websocket?.readyState) {
                //正在连接，下一周期再次查看
                return;
            }
            if (WebSocket.OPEN === WsManager.websocket?.readyState) {
                //连接成功
                Logger.log("websocket重连成功！");
                clearInterval(WsManager.fd);
                WsManager.fd = null;
                return;
            }
            WsManager.initWebsocket();
        }, 10000);
    }
}

export {MsgData, WsManager}
