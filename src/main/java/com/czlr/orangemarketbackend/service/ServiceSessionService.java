package com.czlr.orangemarketbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.enums.ServiceMessageSenderType;
import com.czlr.orangemarketbackend.common.enums.ServiceSessionStatus;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.entity.dto.ServiceMessageDTO;
import com.czlr.orangemarketbackend.entity.dto.ServiceMessagePageDTO;
import com.czlr.orangemarketbackend.entity.dto.ServiceSessionDTO;
import com.czlr.orangemarketbackend.entity.dto.ServiceSessionPageDTO;
import com.czlr.orangemarketbackend.entity.po.ServiceMessage;
import com.czlr.orangemarketbackend.entity.po.ServiceSession;
import com.czlr.orangemarketbackend.entity.po.UserAccount;
import com.czlr.orangemarketbackend.mapper.ServiceMessageMapper;
import com.czlr.orangemarketbackend.mapper.ServiceSessionMapper;
import com.czlr.orangemarketbackend.mapper.UserAccountMapper;
import com.czlr.orangemarketbackend.websocket.ServiceWebSocketSessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ServiceSessionService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final String CLAIMED_NOTICE = "客服已接入";

    private final ServiceSessionMapper serviceSessionMapper;
    private final ServiceMessageMapper serviceMessageMapper;
    private final UserAccountMapper userAccountMapper;
    private final ServiceWebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public ServiceSessionService(
            ServiceSessionMapper serviceSessionMapper,
            ServiceMessageMapper serviceMessageMapper,
            UserAccountMapper userAccountMapper,
            ServiceWebSocketSessionRegistry sessionRegistry,
            ObjectMapper objectMapper) {
        this.serviceSessionMapper = serviceSessionMapper;
        this.serviceMessageMapper = serviceMessageMapper;
        this.userAccountMapper = userAccountMapper;
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ServiceSessionDTO createOrGetActive(Long userId) {
        ServiceSession existing = findActiveByUser(userId);
        if (existing != null) {
            return toSessionDto(existing, nicknameOf(existing.getUserId()));
        }

        ServiceSession session = new ServiceSession();
        session.setUserId(userId);
        session.setStatus(ServiceSessionStatus.ACTIVE);
        serviceSessionMapper.insert(session);
        return toSessionDto(session, nicknameOf(userId));
    }

    public ServiceSessionDTO getCurrent(Long userId) {
        ServiceSession session = findActiveByUser(userId);
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "当前没有进行中的客服会话");
        }
        return toSessionDto(session, nicknameOf(session.getUserId()));
    }

    public ServiceSessionDTO getForUser(Long userId, Long sessionId) {
        return toSessionDto(requireUserSession(userId, sessionId), nicknameOf(userId));
    }

    public ServiceSessionPageDTO listLobby(int page, int pageSize) {
        validatePage(page, pageSize);
        Page<ServiceSession> result = serviceSessionMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<ServiceSession>()
                        .eq(ServiceSession::getStatus, ServiceSessionStatus.ACTIVE)
                        .isNull(ServiceSession::getAgentId)
                        .orderByAsc(ServiceSession::getCreatedAt)
                        .orderByAsc(ServiceSession::getId));
        return toSessionPage(result, page, pageSize);
    }

    public ServiceSessionPageDTO listMine(Long agentId, int page, int pageSize) {
        validatePage(page, pageSize);
        Page<ServiceSession> result = serviceSessionMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<ServiceSession>()
                        .eq(ServiceSession::getStatus, ServiceSessionStatus.ACTIVE)
                        .eq(ServiceSession::getAgentId, agentId)
                        .orderByDesc(ServiceSession::getUpdatedAt)
                        .orderByDesc(ServiceSession::getId));
        return toSessionPage(result, page, pageSize);
    }

    @Transactional
    public ServiceSessionDTO claim(Long agentId, Long sessionId) {
        validateId(sessionId);
        ServiceSession session = serviceSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "客服会话不存在");
        }
        if (session.getStatus() != ServiceSessionStatus.ACTIVE) {
            throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "会话已关闭");
        }
        if (session.getAgentId() != null) {
            if (Objects.equals(session.getAgentId(), agentId)) {
                return toSessionDto(session, nicknameOf(session.getUserId()));
            }
            throw new BusinessException(ResultCode.CONFLICT, "该会话已被其他客服接单");
        }

        int updated = serviceSessionMapper.update(null, new LambdaUpdateWrapper<ServiceSession>()
                .eq(ServiceSession::getId, sessionId)
                .eq(ServiceSession::getStatus, ServiceSessionStatus.ACTIVE)
                .isNull(ServiceSession::getAgentId)
                .set(ServiceSession::getAgentId, agentId));
        if (updated == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该会话已被其他客服接单");
        }

        session.setAgentId(agentId);
        ServiceMessage notice = insertMessage(
                sessionId, ServiceMessageSenderType.SYSTEM, null, CLAIMED_NOTICE);
        ServiceSessionDTO dto = toSessionDto(session, nicknameOf(session.getUserId()));
        pushToSessionMembers(session, claimedEvent(session));
        pushToSessionMembers(session, chatEvent(notice));
        return dto;
    }

    @Transactional
    public void closeByUser(Long userId, Long sessionId) {
        close(requireUserSession(userId, sessionId), ServiceMessageSenderType.USER, userId);
    }

    @Transactional
    public void closeByAgent(Long agentId, Long sessionId) {
        ServiceSession session = requireExisting(sessionId);
        if (!Objects.equals(session.getAgentId(), agentId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能关闭自己接待的会话");
        }
        close(session, ServiceMessageSenderType.AGENT, agentId);
    }

    public ServiceMessagePageDTO listMessagesForUser(Long userId, Long sessionId, int page, int pageSize) {
        requireUserSession(userId, sessionId);
        return listMessages(sessionId, page, pageSize);
    }

    public ServiceMessagePageDTO listMessagesForAgent(Long agentId, Long sessionId, int page, int pageSize) {
        ServiceSession session = requireExisting(sessionId);
        if (session.getAgentId() != null && !Objects.equals(session.getAgentId(), agentId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该会话消息");
        }
        return listMessages(sessionId, page, pageSize);
    }

    @Transactional
    public ServiceMessageDTO sendChat(Long senderId, boolean agent, Long sessionId, String rawContent) {
        String content = requireContent(rawContent);
        ServiceSession session = requireExisting(sessionId);
        if (session.getStatus() != ServiceSessionStatus.ACTIVE) {
            throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "会话已关闭");
        }

        ServiceMessageSenderType senderType;
        if (agent) {
            if (session.getAgentId() == null) {
                throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "请先接单再回复");
            }
            if (!Objects.equals(session.getAgentId(), senderId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权在该会话中发言");
            }
            senderType = ServiceMessageSenderType.AGENT;
        } else {
            if (!Objects.equals(session.getUserId(), senderId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权在该会话中发言");
            }
            senderType = ServiceMessageSenderType.USER;
        }

        ServiceMessage message = insertMessage(sessionId, senderType, senderId, content);
        pushToSessionMembers(session, chatEvent(message));
        return toMessageDto(message);
    }

    public enum WsPresenceKind {
        JOIN,
        REJOIN,
        LEAVE
    }

    /**
     * WebSocket 在线状态变化：对相关进行中会话写系统消息并推送。
     *
     * @return 是否实际写入了系统消息（没有进行中会话时为 false）
     */
    @Transactional
    public boolean notifyWsPresence(Long personId, boolean asAgent, WsPresenceKind kind) {
        if (personId == null || kind == null) {
            return false;
        }
        List<ServiceSession> sessions = asAgent
                ? listActiveByAgent(personId)
                : listActiveByUser(personId);
        if (sessions.isEmpty()) {
            return false;
        }

        String content = presenceText(asAgent, kind);
        for (ServiceSession session : sessions) {
            ServiceMessage message = insertMessage(
                    session.getId(), ServiceMessageSenderType.SYSTEM, null, content);
            pushToSessionMembers(session, chatEvent(message));
        }
        return true;
    }

    private String presenceText(boolean asAgent, WsPresenceKind kind) {
        return switch (kind) {
            case JOIN -> asAgent ? "客服已进入会话" : "用户已进入会话";
            case REJOIN -> asAgent ? "客服已重新进入会话" : "用户已重新进入会话";
            case LEAVE -> asAgent ? "客服已离开会话" : "用户已离开会话";
        };
    }

    private List<ServiceSession> listActiveByUser(Long userId) {
        return serviceSessionMapper.selectList(new LambdaQueryWrapper<ServiceSession>()
                .eq(ServiceSession::getUserId, userId)
                .eq(ServiceSession::getStatus, ServiceSessionStatus.ACTIVE)
                .orderByDesc(ServiceSession::getId));
    }

    private List<ServiceSession> listActiveByAgent(Long agentId) {
        return serviceSessionMapper.selectList(new LambdaQueryWrapper<ServiceSession>()
                .eq(ServiceSession::getAgentId, agentId)
                .eq(ServiceSession::getStatus, ServiceSessionStatus.ACTIVE)
                .orderByDesc(ServiceSession::getId));
    }

    private void close(
            ServiceSession session,
            ServiceMessageSenderType closedBy,
            Long closerId) {
        if (session.getStatus() != ServiceSessionStatus.ACTIVE) {
            throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "会话已关闭");
        }
        LocalDateTime closedAt = LocalDateTime.now();
        int updated = serviceSessionMapper.update(null, new LambdaUpdateWrapper<ServiceSession>()
                .eq(ServiceSession::getId, session.getId())
                .eq(ServiceSession::getStatus, ServiceSessionStatus.ACTIVE)
                .set(ServiceSession::getStatus, ServiceSessionStatus.CLOSED)
                .set(ServiceSession::getClosedAt, closedAt));
        if (updated == 0) {
            throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "会话状态已变化，请刷新后重试");
        }
        session.setStatus(ServiceSessionStatus.CLOSED);
        session.setClosedAt(closedAt);
        String content = closedBy == ServiceMessageSenderType.AGENT
                ? "客服已结束会话"
                : "用户已结束会话";
        ServiceMessage notice = insertMessage(
                session.getId(), ServiceMessageSenderType.SYSTEM, closerId, content);
        pushToSessionMembers(session, closedEvent(session, closedBy, closerId, notice));
    }

    private ServiceMessagePageDTO listMessages(Long sessionId, int page, int pageSize) {
        validatePage(page, pageSize);
        Page<ServiceMessage> result = serviceMessageMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<ServiceMessage>()
                        .eq(ServiceMessage::getSessionId, sessionId)
                        .orderByAsc(ServiceMessage::getId));
        List<ServiceMessageDTO> list = result.getRecords().stream()
                .map(this::toMessageDto)
                .toList();
        int total = Math.toIntExact(result.getTotal());
        return new ServiceMessagePageDTO(list, total, page, pageSize, (long) page * pageSize < total);
    }

    private ServiceSession findActiveByUser(Long userId) {
        return serviceSessionMapper.selectOne(new LambdaQueryWrapper<ServiceSession>()
                .eq(ServiceSession::getUserId, userId)
                .eq(ServiceSession::getStatus, ServiceSessionStatus.ACTIVE)
                .orderByDesc(ServiceSession::getId)
                .last("LIMIT 1"));
    }

    private ServiceSession requireUserSession(Long userId, Long sessionId) {
        ServiceSession session = requireExisting(sessionId);
        if (!Objects.equals(session.getUserId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该客服会话");
        }
        return session;
    }

    private ServiceSession requireExisting(Long sessionId) {
        validateId(sessionId);
        ServiceSession session = serviceSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "客服会话不存在");
        }
        return session;
    }

    private ServiceMessage insertMessage(
            Long sessionId,
            ServiceMessageSenderType senderType,
            Long senderId,
            String content) {
        ServiceMessage message = new ServiceMessage();
        message.setSessionId(sessionId);
        message.setSenderType(senderType);
        message.setSenderId(senderId);
        message.setContent(content);
        serviceMessageMapper.insert(message);
        return message;
    }

    private void pushToSessionMembers(ServiceSession session, String payload) {
        sessionRegistry.sendToUser(session.getUserId(), payload);
        if (session.getAgentId() != null) {
            sessionRegistry.sendToAgent(session.getAgentId(), payload);
        }
    }

    private String chatEvent(ServiceMessage message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "chat");
        node.put("sessionId", String.valueOf(message.getSessionId()));
        node.put("messageId", String.valueOf(message.getId()));
        node.put("senderType", message.getSenderType().getValue());
        if (message.getSenderId() != null) {
            node.put("senderId", String.valueOf(message.getSenderId()));
        }
        node.put("content", message.getContent());
        if (message.getCreatedAt() != null) {
            node.put("createdAt", message.getCreatedAt().toString());
        }
        return objectMapper.writeValueAsString(node);
    }

    private String claimedEvent(ServiceSession session) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "session_claimed");
        node.put("sessionId", String.valueOf(session.getId()));
        node.put("agentId", String.valueOf(session.getAgentId()));
        return objectMapper.writeValueAsString(node);
    }

    private String closedEvent(
            ServiceSession session,
            ServiceMessageSenderType closedBy,
            Long closerId,
            ServiceMessage notice) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "session_closed");
        node.put("sessionId", String.valueOf(session.getId()));
        node.put("closedBy", closedBy.getValue());
        if (closerId != null) {
            node.put("closerId", String.valueOf(closerId));
        }
        node.put("messageId", String.valueOf(notice.getId()));
        node.put("senderType", ServiceMessageSenderType.SYSTEM.getValue());
        node.put("content", notice.getContent());
        if (notice.getCreatedAt() != null) {
            node.put("createdAt", notice.getCreatedAt().toString());
        }
        return objectMapper.writeValueAsString(node);
    }

    private ServiceSessionPageDTO toSessionPage(Page<ServiceSession> result, int page, int pageSize) {
        List<ServiceSession> records = result.getRecords();
        Map<Long, UserAccount> users = loadUsers(records);
        List<ServiceSessionDTO> list = records.stream()
                .map(session -> {
                    UserAccount user = users.get(session.getUserId());
                    String nickname = user == null ? null : user.getNickname();
                    return toSessionDto(session, nickname);
                })
                .toList();
        int total = Math.toIntExact(result.getTotal());
        return new ServiceSessionPageDTO(list, total, page, pageSize, (long) page * pageSize < total);
    }

    private Map<Long, UserAccount> loadUsers(List<ServiceSession> sessions) {
        Set<Long> userIds = sessions.stream()
                .map(ServiceSession::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UserAccount> accounts = userAccountMapper.selectBatchIds(userIds);
        return accounts.stream().collect(Collectors.toMap(UserAccount::getId, Function.identity()));
    }

    private String nicknameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        UserAccount user = userAccountMapper.selectById(userId);
        return user == null ? null : user.getNickname();
    }

    private ServiceSessionDTO toSessionDto(ServiceSession session, String nickname) {
        return new ServiceSessionDTO(
                String.valueOf(session.getId()),
                String.valueOf(session.getUserId()),
                nickname,
                session.getAgentId() == null ? null : String.valueOf(session.getAgentId()),
                session.getStatus(),
                session.getCreatedAt(),
                session.getClosedAt());
    }

    private ServiceMessageDTO toMessageDto(ServiceMessage message) {
        return new ServiceMessageDTO(
                String.valueOf(message.getId()),
                String.valueOf(message.getSessionId()),
                message.getSenderType(),
                message.getSenderId() == null ? null : String.valueOf(message.getSenderId()),
                message.getContent(),
                message.getCreatedAt());
    }

    private String requireContent(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "消息内容不能为空");
        }
        String content = rawContent.trim();
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "消息内容不能超过" + MAX_CONTENT_LENGTH + "字");
        }
        return content;
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分页参数不合法");
        }
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "会话 ID 不合法");
        }
    }
}
