package com.github.kanon.gateway.component.filter;

import com.alibaba.fastjson.JSONObject;
import com.github.kanon.common.base.config.FilterIgnorePropertiesConfig;
import com.github.kanon.common.base.model.vo.ResponseParam;
import com.github.kanon.common.constants.KanonSecurityConstants;
import com.github.kanon.common.exceptions.ErrorMsgException;
import com.github.kanon.common.utils.system.AuthUtils;
import com.github.tool.common.CollectionUtil;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.netflix.zuul.filters.support.FilterConstants;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

/**
 * @Author: PengCheng
 * @Description: 验证码过滤器，当security.validate.code=true时启用
 * @Date: 2018/9/12
 */
@Slf4j
@RefreshScope
@Configuration("validateCodeFilter")
@ConditionalOnProperty(value = "security.validate.code", havingValue = "true")
public class ValidateCodeFilter extends ZuulFilter {

    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private FilterIgnorePropertiesConfig filterIgnorePropertiesConfig;

    @Override
    public String filterType() {
        return FilterConstants.PRE_TYPE;
    }

    @Override
    public int filterOrder() {
        return FilterConstants.SEND_ERROR_FILTER_ORDER + 1;
    }

    /**
     * 是否校验验证码
     * 1. 判断验证码开关是否开启
     * 2. 判断请求是否登录请求
     * 3. 判断终端是否支持
     * @return true/false
     */
    @Override
    public boolean shouldFilter() {
        HttpServletRequest request = RequestContext.getCurrentContext().getRequest();

        if (!StringUtils.containsIgnoreCase(request.getRequestURI(), AuthUtils.SECURITY_LOGIN_URL)) {
            return false;
        }

        if (CollectionUtil.containAny(filterIgnorePropertiesConfig.getClients(),Arrays.asList(AuthUtils.extractAndDecodeHeader(request)))) {
            return false;
        }
        return true;
    }

    @Override
    public Object run() {
        try {
            checkCode(RequestContext.getCurrentContext().getRequest());
        } catch (ErrorMsgException e) {
            RequestContext ctx = RequestContext.getCurrentContext();
            ResponseParam result = new ResponseParam(478,null);

            ctx.setResponseStatusCode(478);
            ctx.setSendZuulResponse(false);
            ctx.getResponse().setContentType("application/json;charset=UTF-8");
            ctx.setResponseBody(JSONObject.toJSONString(result));
        }
        return null;
    }

    /**
     * 检查code
     * @param httpServletRequest request
     * @throws ErrorMsgException 验证码校验异常
     */
    private void checkCode(HttpServletRequest httpServletRequest) throws ErrorMsgException {
        //TODO 这里可以改进
        String code = httpServletRequest.getParameter("code");
        if (StringUtils.isEmpty(code)) {
            throw new ErrorMsgException("请输入验证码");
        }

        String randomStr = httpServletRequest.getParameter("randomStr");
        if (StringUtils.isEmpty(randomStr)) {
            randomStr = httpServletRequest.getParameter("mobile");
        }

        //从redis中抓取当前用户验证码进行验证码校验
        String key = KanonSecurityConstants.VERIFICATION_CODE_CACHE_KEY + randomStr;
        if (!redisTemplate.hasKey(key)) {
            throw new ErrorMsgException("验证码已过期，请重新获取");
        }

        Object codeObj = redisTemplate.opsForValue().get(key);
        if (codeObj == null) {
            throw new ErrorMsgException("验证码已过期，请重新获取");
        }

        String saveCode = codeObj.toString();
        if (StringUtils.isBlank(saveCode)) {
            redisTemplate.delete(key);
            throw new ErrorMsgException("验证码已过期，请重新获取");
        }

        if (!StringUtils.equals(saveCode, code)) {
            redisTemplate.delete(key);
            throw new ErrorMsgException("验证码错误，请重新输入");
        }

        redisTemplate.delete(key);
    }
}
