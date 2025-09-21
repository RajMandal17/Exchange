package com.custom.matchingengine.message;

import com.custom.matchingengine.command.Command;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommandStartMessage extends Message {
    private Command command;
    private long commandOffset;

    public CommandStartMessage() {
        this.setMessageType(MessageType.COMMAND_START);
    }
}
