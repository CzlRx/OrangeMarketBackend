package com.czlr.orangemarketbackend.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.czlr.orangemarketbackend.common.ResultCode;
import com.czlr.orangemarketbackend.common.enums.CommonStatus;
import com.czlr.orangemarketbackend.common.enums.OrderStatus;
import com.czlr.orangemarketbackend.common.exception.BusinessException;
import com.czlr.orangemarketbackend.entity.dto.AdminShipmentDTO;
import com.czlr.orangemarketbackend.entity.dto.AdminUserStatusDTO;
import com.czlr.orangemarketbackend.entity.dto.ShipOrderRequest;
import com.czlr.orangemarketbackend.entity.po.Order;
import com.czlr.orangemarketbackend.entity.po.UserAccount;
import com.czlr.orangemarketbackend.mapper.OrderMapper;
import com.czlr.orangemarketbackend.mapper.UserAccountMapper;
import com.czlr.orangemarketbackend.utils.AuthRedisKey;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Service
public class AdminService {

    private static final int TRACKING_NO_MAX_LENGTH = 128;

    private final OrderMapper orderMapper;
    private final UserAccountMapper userAccountMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public AdminService(
            OrderMapper orderMapper,
            UserAccountMapper userAccountMapper,
            @Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.orderMapper = orderMapper;
        this.userAccountMapper = userAccountMapper;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public AdminShipmentDTO shipOrder(Long orderId, ShipOrderRequest request) {
        validateId(orderId, "orderId");
        String trackingNo = requireTrackingNo(request);

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (order.getStatus() != OrderStatus.PENDING_SHIPMENT) {
            throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "仅待发货订单可以发货");
        }

        LocalDateTime shippedAt = LocalDateTime.now();
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderStatus.PENDING_SHIPMENT)
                .set(Order::getStatus, OrderStatus.PENDING_RECEIPT)
                .set(Order::getTrackingNo, trackingNo)
                .set(Order::getShippedAt, shippedAt));
        if (updated == 0) {
            throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "订单状态已发生变化，请刷新后重试");
        }

        return new AdminShipmentDTO(
                String.valueOf(order.getId()),
                order.getOrderNo(),
                OrderStatus.PENDING_RECEIPT,
                trackingNo,
                shippedAt);
    }

    @Transactional
    public AdminUserStatusDTO banUser(Long userId) {
        validateId(userId, "userId");

        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (isAdmin(user.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能封禁管理员账号");
        }

        if (user.getStatus() != CommonStatus.DISABLED) {
            int updated = userAccountMapper.update(null, new LambdaUpdateWrapper<UserAccount>()
                    .eq(UserAccount::getId, userId)
                    .eq(UserAccount::getStatus, CommonStatus.ACTIVE)
                    .set(UserAccount::getStatus, CommonStatus.DISABLED));
            if (updated == 0) {
                UserAccount latestUser = userAccountMapper.selectById(userId);
                if (latestUser == null) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
                }
                if (latestUser.getStatus() != CommonStatus.DISABLED) {
                    throw new BusinessException(ResultCode.BUSINESS_STATE_CONFLICT, "用户状态已发生变化，请刷新后重试");
                }
                user = latestUser;
            } else {
                user.setStatus(CommonStatus.DISABLED);
            }
        }

        invalidateSessions(userId);
        return new AdminUserStatusDTO(String.valueOf(user.getId()), user.getStatus(), user.getRole());
    }

    private String requireTrackingNo(ShipOrderRequest request) {
        if (request == null || request.getTrackingNo() == null || request.getTrackingNo().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "trackingNo 不能为空");
        }
        String trackingNo = request.getTrackingNo().trim();
        if (trackingNo.length() > TRACKING_NO_MAX_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "trackingNo 长度不能超过 128 个字符");
        }
        return trackingNo;
    }

    private void validateId(Long id, String parameterName) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, parameterName + " 参数错误");
        }
    }

    private boolean isAdmin(String role) {
        return role != null && "admin".equalsIgnoreCase(role.trim());
    }

    private void invalidateSessions(Long userId) {
        Set<String> candidateKeys = redisTemplate.keys(AuthRedisKey.loginPattern(userId));
        if (candidateKeys == null || candidateKeys.isEmpty()) {
            return;
        }

        Set<String> userSessionKeys = new HashSet<>();
        for (String key : candidateKeys) {
            Object sessionUserId = redisTemplate.opsForHash().get(key, "id");
            if (sessionUserId != null && userId.toString().equals(sessionUserId.toString())) {
                userSessionKeys.add(key);
            }
        }
        if (!userSessionKeys.isEmpty()) {
            redisTemplate.delete(userSessionKeys);
        }
    }
}
