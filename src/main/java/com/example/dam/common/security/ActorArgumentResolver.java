package com.example.dam.common.security;

import com.example.dam.common.domain.Actor;
import com.example.dam.common.domain.InvalidRequestException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves the calling {@link Actor} from the {@code X-User-Id} header.
 * <p>
 * POC stand-in for an identity provider: in production this would read the authenticated principal from
 * Spring Security. Callers can never impersonate system components or AI agents.
 */
public class ActorArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String USER_HEADER = "X-User-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Actor.class.equals(parameter.getParameterType());
    }

    @Override
    public Actor resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                 NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String userId = webRequest.getHeader(USER_HEADER);
        if (userId == null || userId.isBlank()) {
            return Actor.anonymous();
        }
        try {
            return Actor.human(userId);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("INVALID_ACTOR", e.getMessage());
        }
    }
}
