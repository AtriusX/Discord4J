/*
 * This file is part of Discord4J.
 *
 * Discord4J is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Discord4J is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Discord4J.  If not, see <http://www.gnu.org/licenses/>.
 */
package discord4j.core.event.domain.channel;

import discord4j.core.GatewayAggregate;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.gateway.ShardInfo;

/**
 * Dispatched when a {@link VoiceChannel} is deleted in a guild.
 * <p>
 * This event is dispatched by Discord.
 *
 * @see <a href="https://discordapp.com/developers/docs/topics/gateway#channel-delete">Channel Delete</a>
 */
public class VoiceChannelDeleteEvent extends ChannelEvent {

    private final VoiceChannel channel;

    public VoiceChannelDeleteEvent(GatewayAggregate gateway, ShardInfo shardInfo, VoiceChannel channel) {
        super(gateway, shardInfo);
        this.channel = channel;
    }

    /**
     * Gets the {@link VoiceChannel} that has been deleted in this event.
     *
     * @return The deleted {@link VoiceChannel}.
     */
    public VoiceChannel getChannel() {
        return channel;
    }

    @Override
    public String toString() {
        return "VoiceChannelDeleteEvent{" +
                "channel=" + channel +
                '}';
    }
}
