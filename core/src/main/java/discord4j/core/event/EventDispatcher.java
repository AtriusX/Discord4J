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

package discord4j.core.event;

import discord4j.core.event.domain.Event;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxProcessor;
import reactor.core.scheduler.Scheduler;
import reactor.util.Logger;
import reactor.util.Loggers;

/**
 * Distributes events to subscribers. {@link Event} instances can be published over this class and dispatched to all
 * subscribers.
 * <p>
 * Individual events can be published to subscribers using {@link #publish(Event)} while they can be used to consumed
 * through {@link #on(Class)} giving the proper {@link Event} class as argument.
 * <p>
 * Uses an underlying {@link FluxProcessor} that must be supplied on construction, as well as a {@link Scheduler} to
 * define subscriber thread affinity.
 * <p>
 * Each event can be consumed using the following pattern:
 * <pre>
 *     dispatcher.on(MessageCreatedEvent.class)
 *           .subscribe(event -&gt; event.getMessage());
 * </pre>
 * While events can be published through:
 * <pre>
 *     fluxOfEvents.doOnNext(dispatcher::publish)
 *           .subscribe();
 * </pre>
 */
public class EventDispatcher {

    private final FluxProcessor<Event, Event> processor;
    private final Scheduler scheduler;

    /**
     * Creates a new event dispatcher using the given processor and thread model.
     *
     * @param processor a FluxProcessor of Event types, used to bridge gateway events to the dispatcher subscribers
     * @param scheduler a Scheduler to ensure a certain thread model on each published signal
     */
    public EventDispatcher(FluxProcessor<Event, Event> processor, Scheduler scheduler) {
        this.processor = processor;
        this.scheduler = scheduler;
    }

    /**
     * Retrieves a {@link Flux} with elements of the given {@link Event} type.
     *
     * @param eventClass the event class to obtain events from
     * @param <T> the type of the event class
     * @return a new {@link reactor.core.publisher.Flux} with the requested events
     */
    public <T extends Event> Flux<T> on(Class<T> eventClass) {
        return processor.publishOn(scheduler)
                .ofType(eventClass)
                .doOnNext(event -> {
                    int shard = event.getClient().getConfig().getShardIndex();
                    Logger log = Loggers.getLogger("discord4j.events." + eventClass.getSimpleName() + "." + shard);
                    if (log.isDebugEnabled()) {
                        log.debug("{}", event);
                    }
                });
    }

    /**
     * Publishes an {@link Event} to the dispatcher.
     *
     * @param event the {@link Event} to publish
     */
    public void publish(Event event) {
        processor.onNext(event);
    }
}
