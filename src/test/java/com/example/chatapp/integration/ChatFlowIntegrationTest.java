package com.example.chatapp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.chatapp.constants.RedisKeys;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end tests against a real Redis (Testcontainers). Skipped automatically when Docker is unavailable.
 * Maps to the assignment's Test Cases 1-5.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class ChatFlowIntegrationTest {

    private static final String BASE = "/api/chatapp/chatrooms";

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private RedisMessageListenerContainer listenerContainer;

    private static String uniqueRoom() {
        return "room-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void createRoom(String room) throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomName\":\"" + room + "\"}"))
                .andExpect(status().isCreated());
    }

    private void join(String room, String user) throws Exception {
        mockMvc.perform(post(BASE + "/" + room + "/join").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participant\":\"" + user + "\"}"))
                .andExpect(status().isOk());
    }

    private void send(String room, String user, String text) throws Exception {
        mockMvc.perform(post(BASE + "/" + room + "/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participant\":\"" + user + "\",\"message\":\"" + text + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void testCase1_createAndJoin_storesMetadataAndParticipant() throws Exception {
        String room = uniqueRoom();

        createRoom(room);
        join(room, "guest_user");

        assertThat(redis.opsForHash().get(RedisKeys.roomMeta(room), "roomId")).isEqualTo(room);
        assertThat(redis.opsForSet().isMember(RedisKeys.participants(room), "guest_user")).isTrue();
    }

    @Test
    void testCase2_sendAndRetrieve_messagesAreInOrder() throws Exception {
        String room = uniqueRoom();
        createRoom(room);
        join(room, "guest_user");

        send(room, "guest_user", "first");
        send(room, "guest_user", "second");

        mockMvc.perform(get(BASE + "/" + room + "/messages").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].message").value("second"));

        mockMvc.perform(get(BASE + "/" + room + "/messages").param("limit", "10"))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].message").value("first"))
                .andExpect(jsonPath("$.messages[1].message").value("second"))
                .andExpect(jsonPath("$.messages[0].participant").value("guest_user"))
                .andExpect(jsonPath("$.messages[0].timestamp").exists());
    }

    @Test
    void testCase3_realTime_subscribersReceivePublishedMessage() throws Exception {
        String room = uniqueRoom();
        createRoom(room);
        join(room, "guest_user");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();
        MessageListener listener = (message, pattern) -> {
            received.set(new String(message.getBody()));
            latch.countDown();
        };
        ChannelTopic topic = new ChannelTopic(RedisKeys.channel(room));
        listenerContainer.addMessageListener(listener, topic);
        try {
            Thread.sleep(500); // give Redis time to register the subscription
            send(room, "guest_user", "Hello, everyone!");

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(received.get()).contains("Hello, everyone!").contains("guest_user");
        } finally {
            listenerContainer.removeMessageListener(listener, topic);
        }
    }

    @Test
    void testCase4_errorHandling() throws Exception {
        String room = uniqueRoom();

        // sending to a room that does not exist
        mockMvc.perform(post(BASE + "/ghost-room/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participant\":\"guest_user\",\"message\":\"Hi\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"));

        // duplicate room name
        createRoom(room);
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomName\":\"" + room + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"));

        // sender who never joined
        mockMvc.perform(post(BASE + "/" + room + "/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participant\":\"stranger\",\"message\":\"Hi\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCase5_deleteRoom_removesAllKeys() throws Exception {
        String room = uniqueRoom();
        createRoom(room);
        join(room, "guest_user");
        send(room, "guest_user", "bye");

        mockMvc.perform(delete(BASE + "/" + room))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        assertThat(redis.hasKey(RedisKeys.roomMeta(room))).isFalse();
        assertThat(redis.hasKey(RedisKeys.participants(room))).isFalse();
        assertThat(redis.hasKey(RedisKeys.messages(room))).isFalse();
        assertThat(redis.opsForSet().isMember(RedisKeys.ROOM_INDEX, room)).isFalse();

        mockMvc.perform(delete(BASE + "/" + room)).andExpect(status().isNotFound());
    }
}
