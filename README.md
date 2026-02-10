# Redis Cluster Pub/Sub Chat

Redis Cluster 기반 Pub/Sub으로 실시간 스트리밍 채팅을 제공하는 Spring Boot 프로젝트.

## Overview
- WebSocket(STOMP) 기반 실시간 채팅
- Redis Cluster Pub/Sub으로 메시지 브로드캐스트
- 채팅방 생성/조회 API 제공

## Architecture
- Client -> WebSocket `/ws/chat`
- Client -> `/pub/chat/message`로 메시지 발행
- 서버는 Redis Pub/Sub에 메시지 publish
- RedisSubscriber가 메시지 수신 후 `/sub/chat/room/{roomId}`로 브로드캐스트

## Tech Stack
- Java 21
- Spring Boot 3.2.x
- Spring WebSocket (STOMP)
- Spring Data Redis (Lettuce)
- Redis 7 (Cluster)
- Docker Compose

## Getting Started

### Prerequisites
- JDK 21
- Docker + Docker Compose

### 1) Redis Cluster 실행
```bash
docker compose up -d
```

### 2) 클러스터 초기 생성
```bash
docker exec -it redis-node-1 redis-cli --cluster create \
  redis-node-1:7000 redis-node-2:7001 redis-node-3:7002 \
  redis-node-4:7003 redis-node-5:7004 redis-node-6:7005 \
  --cluster-replicas 1
```

### 3) 애플리케이션 실행
```bash
./gradlew bootRun
```

### 4) 채팅방 생성 예시
```bash
curl -X POST "http://localhost:8080/chat/room?name=demo"
```

## Configuration
`src/main/resources/application.properties`
```properties
spring.redis.cluster.nodes=localhost:7000,localhost:7001,localhost:7002,localhost:7003,localhost:7004,localhost:7005
server.port=8080
```

## REST API
- `GET /chat/rooms` 모든 채팅방 조회
- `GET /chat/room/{roomId}` 특정 채팅방 조회
- `POST /chat/room?name={name}` 채팅방 생성

## WebSocket
- Handshake: `/ws/chat` (SockJS 지원)
- Publish: `/pub/chat/message`
- Subscribe: `/sub/chat/room/{roomId}`

### Message Payload
```json
{
  "type": "ENTER",
  "roomId": "room-id",
  "sender": "user",
  "message": "hello"
}
```
`type`: `ENTER`, `TALK`, `QUIT`

## Notes (Local Dev)
Redis Cluster가 컨테이너 호스트네임(`redis-node-*`)으로 노드를 광고하므로,
로컬 앱에서 연결하려면 다음 중 하나가 필요합니다.

- 애플리케이션에서 host remap 적용 (Lettuce `MappingSocketAddressResolver`)
- 또는 로컬 `/etc/hosts`에 `redis-node-1~6` 매핑 추가

## Troubleshooting
- `UnknownHostException redis-node-*`
  로컬 앱이 컨테이너 호스트네임을 해석하지 못함 -> 위 Notes 참고
