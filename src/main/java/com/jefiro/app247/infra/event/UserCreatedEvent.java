package com.jefiro.app247.infra.event;

import com.jefiro.app247.domain.model.auth.User;

public record UserCreatedEvent(User user){
}
