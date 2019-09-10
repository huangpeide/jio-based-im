package cn.ideamake.components.im.listener;

import cn.ideamake.components.im.common.common.ImConfig;
import cn.ideamake.components.im.common.common.ImConst;
import cn.ideamake.components.im.common.common.ImPacket;
import cn.ideamake.components.im.common.common.ImSessionContext;
import cn.ideamake.components.im.common.common.cache.redis.RedisCache;
import cn.ideamake.components.im.common.common.cache.redis.RedisCacheManager;
import cn.ideamake.components.im.common.common.cache.redis.RedissonTemplate;
import cn.ideamake.components.im.common.common.message.MessageHelper;
import cn.ideamake.components.im.common.common.packets.ChatBody;
import cn.ideamake.components.im.common.common.packets.Client;
import cn.ideamake.components.im.common.common.packets.User;
import cn.ideamake.components.im.common.common.utils.ChatKit;
import cn.ideamake.components.im.common.common.utils.ImKit;
import cn.ideamake.components.im.common.constants.Constants;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RMapCache;
import org.springframework.stereotype.Service;
import org.tio.core.ChannelContext;
import org.tio.core.intf.Packet;
import org.tio.server.intf.ServerAioListener;

@Slf4j
@Service
public class ImServerAioListener implements ServerAioListener {


    @Setter
    private ImConfig imConfig;

//    static RedisCache userCache = RedisCacheManager.getCache(ImConst.USER);

    public ImServerAioListener() {
    }

//    public ImServerAioListener(ImConfig imConfig) {
//        this.imConfig = imConfig;
//    }

    /**
     * 建链后触发本方法，注：建链不一定成功，需要关注参数isConnected
     *
     * @param channelContext
     * @param isConnected    是否连接成功,true:表示连接成功，false:表示连接失败
     * @param isReconnect    是否是重连, true: 表示这是重新连接，false: 表示这是第一次连接
     * @throws Exception
     * @author: WChao
     */
    @Override
    public void onAfterConnected(ChannelContext channelContext, boolean isConnected, boolean isReconnect) {
        return;
    }

    /**
     * 消息包发送之后触发本方法
     *
     * @param channelContext
     * @param packet
     * @param isSentSuccess  true:发送成功，false:发送失败
     * @throws Exception
     * @author WChao
     */
    @Override
    public void onAfterSent(ChannelContext channelContext, Packet packet, boolean isSentSuccess) {
    }

    /**
     * 连接关闭前触发本方法
     *
     * @param channelContext the channelcontext
     * @param throwable      the throwable 有可能为空
     * @param remark         the remark 有可能为空
     * @param isRemove
     * @throws Exception
     * @author WChao
     */
    @Override
    public void onBeforeClose(ChannelContext channelContext, Throwable throwable, String remark, boolean isRemove) {
        if (imConfig == null) {
            return;
        }
        MessageHelper messageHelper = imConfig.getMessageHelper();
        if (messageHelper != null) {
            ImSessionContext imSessionContext = (ImSessionContext) channelContext.getAttribute();
            if (imSessionContext == null) {
                return;
            }
            Client client = imSessionContext.getClient();
            if (client == null) {
                return;
            }
            User onlineUser = client.getUser();
            if (onlineUser == null) {
                return;
            }
            messageHelper.getBindListener().initUserTerminal(channelContext, onlineUser.getTerminal(), ImConst.OFFLINE);
        }
    }

    /**
     * 解码成功后触发本方法
     *
     * @param channelContext
     * @param packet
     * @param packetSize
     * @throws Exception
     * @author: WChao
     */
    @Override
    public void onAfterDecoded(ChannelContext channelContext, Packet packet, int packetSize) throws Exception {

    }

    /**
     * 接收到TCP层传过来的数据后
     *
     * @param channelContext
     * @param receivedBytes  本次接收了多少字节
     * @throws Exception
     */
    @Override
    public void onAfterReceivedBytes(ChannelContext channelContext, int receivedBytes) throws Exception {

    }

    /**
     * 处理完一个消息包的后续操作
     *
     * @param channelContext
     * @param packet
     * @param cost           本次处理消息耗时，单位：毫秒
     * @throws Exception
     */
    @Override
    public void onAfterHandled(ChannelContext channelContext, Packet packet, long cost) throws Exception {
        log.info("onAfter");
        ImPacket imPacket = (ImPacket) packet;
        ChatBody chatBody = ChatKit.toChatBody(imPacket.getBody(), channelContext);
        //此处做好友关系处理,暂时对每条消息都检查用户好友关系，没有就做添加处理,用户只有再授权登录后才会再im系统中被记录
        if (chatBody != null && !StringUtils.isEmpty(chatBody.getFrom()) && !StringUtils.isEmpty(chatBody.getTo())) {
            String key = chatBody.getFrom() + ":" + Constants.USER.INFO;
            log.info(key);
            User sender = RedisCacheManager.getCache(ImConst.USER).get(key,User.class);
//            User sender = (User) RedissonTemplate.me().getRedissonClient().getMapCache(Constants.USER.PREFIX + chatBody.getFrom() + Constants.USER.INFO);
            //发送者信息未被初始化,正常情况下应用和im双方用户数据需要打通，暂不考虑发送方不存在的情况，会被记录，但是不纪录用户列表
            if (sender == null) {
                log.error("发送者{}信息未被初始化", chatBody.getFrom());
                //此处后续可以向三方服务器拉取用户信息
                return;
            }
            User receiver= RedisCacheManager.getCache(ImConst.USER).get(key,User.class);
            if (receiver == null) {
                log.error("接收者{}信息未被初始化", chatBody.getTo());
                return;
            }
            //当发送者和接收者信息都不为空时对双方的好友列表做存储
            RMapCache<String, User> friendsOfSender = RedissonTemplate.me().getRedissonClient().getMapCache(Constants.USER.PREFIX + ":" + chatBody.getFrom() + ":" + Constants.USER.FRIENDS);


            //发送者好友列表没有接收者时，将接收者添加到其好友列表
            if (friendsOfSender.isEmpty() || !friendsOfSender.containsKey(chatBody.getTo())) {
                log.info("发送者[{}]好友列表中不存在[{}],做追加操作",sender.getNick(),receiver.getNick());
        		User receiverSimple = ImKit.copyUserWithoutFriendsGroups(receiver);
        		friendsOfSender.put(chatBody.getTo(),receiver);
            }

            RMapCache<String, User> friendsOfReceiver = RedissonTemplate.me().getRedissonClient().getMapCache(Constants.USER.PREFIX + ":" + chatBody.getTo() + ":" + Constants.USER.FRIENDS);
            //同样检查接收者好友列表，若没有发送时，将接收者添加到其好友列表
            if (friendsOfReceiver.isEmpty() || !friendsOfReceiver.containsKey(chatBody.getFrom())) {
                log.info("接收者[{}]好友列表中不存在[{}],做追加操作",receiver.getNick(),sender.getNick());
        		User senderSimple = ImKit.copyUserWithoutFriendsGroups(receiver);
        		friendsOfReceiver.put(chatBody.getFrom(),senderSimple);
            }
            log.info(chatBody.toString());
        }
    }

}