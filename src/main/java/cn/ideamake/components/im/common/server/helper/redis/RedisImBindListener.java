package cn.ideamake.components.im.common.server.helper.redis;

import org.apache.commons.lang3.StringUtils;
import cn.ideamake.components.im.common.common.ImConfig;
import cn.ideamake.components.im.common.common.ImSessionContext;
import cn.ideamake.components.im.common.common.cache.redis.RedisCache;
import cn.ideamake.components.im.common.common.cache.redis.RedisCacheManager;
import cn.ideamake.components.im.common.common.listener.AbstractImBindListener;
import cn.ideamake.components.im.common.common.packets.Client;
import cn.ideamake.components.im.common.common.packets.Group;
import cn.ideamake.components.im.common.common.packets.User;
import cn.ideamake.components.im.common.common.utils.ImKit;
import org.tio.core.ChannelContext;

import java.io.Serializable;
import java.util.List;

/**
 * 消息持久化绑定监听器
 * @author WChao
 * @date 2018年4月8日 下午4:12:31
 */
public class RedisImBindListener extends AbstractImBindListener {
	
	private RedisCache groupCache = null;
	private RedisCache userCache = null;
	private final String SUBFIX = ":";
	
	public RedisImBindListener(){
		this(null);
	}
	
	public RedisImBindListener(ImConfig imConfig){
		this.imConfig = imConfig;
		groupCache = RedisCacheManager.getCache(GROUP);
		userCache = RedisCacheManager.getCache(USER);
	}
	
	static{
		RedisCacheManager.register(USER, Integer.MAX_VALUE, Integer.MAX_VALUE);
		RedisCacheManager.register(GROUP, Integer.MAX_VALUE, Integer.MAX_VALUE);
		RedisCacheManager.register(STORE, Integer.MAX_VALUE, Integer.MAX_VALUE);
		RedisCacheManager.register(PUSH, Integer.MAX_VALUE, Integer.MAX_VALUE);
		
	}
	
	@Override
	public void onAfterGroupBind(ChannelContext channelContext, String group) throws Exception {
		if(!isStore()) {
			return;
		}
		initGroupUsers(group,channelContext);
	}

	@Override
	public void onAfterGroupUnbind(ChannelContext channelContext, String group) throws Exception {
		if(!isStore()) {
			return;
		}
		String userid = channelContext.getUserid();
		//移除群组成员;
		groupCache.listRemove(group+SUBFIX+USER, userid);
		//移除成员群组;
		userCache.listRemove(userid+SUBFIX+GROUP, group);
		RedisCacheManager.getCache(PUSH).remove(GROUP+SUBFIX+group+SUBFIX+userid);
	}

	@Override
	public void onAfterUserBind(ChannelContext channelContext, String userid) throws Exception {
		if(!isStore()) {
			return;
		}
		ImSessionContext imSessionContext = (ImSessionContext)channelContext.getAttribute();
		Client client = imSessionContext.getClient();
		if(client == null) {
			return;
		}
		User onlineUser = client.getUser();
		if(onlineUser != null){
			initUserTerminal(channelContext,onlineUser.getTerminal(),ONLINE);
			initUserInfo(onlineUser);
		}
	}

	@Override
	public void onAfterUserUnbind(ChannelContext channelContext, String userid) throws Exception {
		if(!isStore()) {
			return;
		}
		
	}
	/**
	 * 初始化群组用户;
	 * @param groupId
	 * @param channelContext
	 */
	public void initGroupUsers(String groupId ,ChannelContext channelContext){
		if(!isStore()) {
			return;
		}
		String userId = channelContext.getUserid();
		if(StringUtils.isEmpty(groupId) || StringUtils.isEmpty(userId)) {
			return;
		}
		String group_user_key = groupId+SUBFIX+USER;
		List<String> users = groupCache.listGetAll(group_user_key);
		if(!users.contains(userId)){
			groupCache.listPushTail(group_user_key, userId);
		}
		initUserGroups(userId, groupId);
		
		ImSessionContext imSessionContext = (ImSessionContext)channelContext.getAttribute();
		Client client = imSessionContext.getClient();
		if(client == null) {
			return;
		}
		User onlineUser = client.getUser();
		if(onlineUser == null) {
			return;
		}
		List<Group> groups = onlineUser.getGroups();
		if(groups == null) {
			return;
		}
		for(Group group : groups){
			if(groupId.equals(group.getGroupId())){
				groupCache.put(groupId+SUBFIX+INFO, group);
				break;
			}
		}
	}
	/**
	 * 初始化用户拥有哪些群组;
	 * @param userid
	 * @param group
	 */
	public void initUserGroups(String userid, String group){
		if(!isStore()) {
			return;
		}
		if(StringUtils.isEmpty(group) || StringUtils.isEmpty(userid)) {
			return;
		}
		List<String> groups = userCache.listGetAll(userid+SUBFIX+GROUP);
		if(!groups.contains(group)){
			userCache.listPushTail(userid+SUBFIX+GROUP, group);
		}
	}
	/**
	 * 初始化用户终端协议类型;
	 * @param channelContext
	 * @param terminal
	 * @param status(online、offline)
	 */
	@Override
	public void initUserTerminal(ChannelContext channelContext , String terminal , String status){
		if(!isStore()) {
			return;
		}
		String userId = channelContext.getUserid();
		if(StringUtils.isEmpty(userId) || StringUtils.isEmpty(terminal)) {
			return;
		}
		userCache.put(userId+SUBFIX+TERMINAL+SUBFIX+terminal, status);
	}
	/**
	 * 初始化用户终端协议类型;
	 * @param user
	 */
	public void initUserInfo(User user){
		if(!isStore() || user == null) {
			return;
		}
		String userId = user.getId();
		if(StringUtils.isEmpty(userId)) {
			return;
		}
		User userCopy = ImKit.copyUserWithoutFriendsGroups(user);
		userCache.put(userId+SUBFIX+INFO, userCopy);
		List<Group> friends = user.getFriends();
		if(friends != null){
			userCache.put(userId+SUBFIX+FRIENDS, (Serializable) friends);
		}
	}
	/**
	 * 是否开启持久化;
	 * @return
	 */
	public boolean isStore(){
		return ON.equals(imConfig.getIsStore());
	}
}
