package com.pickbit.jobpostingservice.domain.port;

/**
 * 조회 이력 비동기 메시지 큐 — push/poll/size
 */
public interface ViewMessageQueue {

    void push(String message);

    String poll();

    Long size();
}
