# Redis Cluster Pub/Sub Chat

Redis Cluster 기반 Pub/Sub으로 실시간 스트리밍 채팅을 제공하는 Spring Boot 프로젝트.

## 개요
- WebSocket(STOMP) 기반 실시간 채팅
- Redis Cluster Pub/Sub으로 메시지 브로드캐스트
- 채팅방 생성/조회 API 제공

## 아키텍처
- Client -> WebSocket `/ws/chat`
- Client -> `/pub/chat/message`로 메시지 발행
- 서버는 Redis Pub/Sub에 메시지 publish
- RedisSubscriber가 메시지 수신 후 `/sub/chat/room/{roomId}`로 브로드캐스트

## 기술 스택
- Java 21
- Spring Boot
- Spring WebSocket (STOMP)
- Spring Data Redis (Lettuce)
- Redis 7 (Cluster)
- Docker Compose

## 실행 방식(권장: Docker 단일 chat-server)

### 사전 준비
1. Docker / Docker Compose
2. Redis Stream(6379) 컨테이너가 먼저 실행되어 있어야 함
- `RedisStreamAndMongo`의 redis 컨테이너(`redis-stream`) 실행 필요

### 1) Redis Cluster 실행
```bash
cd streaming-chat-server
docker compose up -d redis-node-1 redis-node-2 redis-node-3 redis-node-4 redis-node-5 redis-node-6
```

### 2) 클러스터 초기 생성
```bash
docker exec -it redis-node-1 redis-cli --cluster create \
  redis-node-1:7000 redis-node-2:7001 redis-node-3:7002 \
  redis-node-4:7003 redis-node-5:7004 redis-node-6:7005 \
  --cluster-replicas 1
```

### 3) chat-server 컨테이너 실행(단일 1개)
```bash
docker compose up -d chat-server
```

### 4) 확인
```bash
curl http://localhost:8080/chat/rooms
```

## 리소스 제한(chat-server)
`streaming-chat-server/docker-compose.yml`에 다음 제한이 적용된다.
- CPU: `2.0`
- Memory limit: `4g`
- Memory reservation: `2g`

## 로그
- chat-server 로그 파일: `streaming-chat-server/server1.log`
- 컨테이너 로그 확인:
```bash
docker logs -f chat-server
```

## 종료
```bash
cd streaming-chat-server
docker compose stop chat-server
docker compose down -v
```

## 참고: 로컬 Java 실행(레거시)
기존 `./gradlew clean build` + `./start_servers.sh` 방식도 사용할 수 있지만,
현재 테스트 기준은 chat-server Docker 단일 컨테이너 실행이다.
