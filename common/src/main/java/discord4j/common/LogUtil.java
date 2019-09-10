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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Discord4J. If not, see <http://www.gnu.org/licenses/>.
 */

package discord4j.common;

import reactor.util.context.Context;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LogUtil {

    public static String format(Context context, String msg) {
        String header = Stream.of(
                context.getOrEmpty("bucket").map(id -> "bucket=" + id),
                context.getOrEmpty("request").map(id -> "request=" + id),
                context.getOrEmpty("gateway").map(id -> "gateway=" + id),
                context.getOrEmpty("shard").map(id -> "shard=" + id))
                .map(opt -> opt.orElse(""))
                .filter(str -> !str.isEmpty())
                .collect(Collectors.joining(", "));
        StringBuilder builder = new StringBuilder();
        if (!header.isEmpty()) {
            return builder.append('[')
                    .append(header)
                    .append("] ")
                    .append(msg)
                    .toString();
        } else {
            return msg;
        }
    }
}
