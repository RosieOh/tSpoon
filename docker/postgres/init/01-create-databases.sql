-- Database per Service: 서비스마다 자기 데이터베이스만 사용한다.
-- postgres 컨테이너가 빈 볼륨으로 처음 시작될 때 한 번만 실행된다.
CREATE DATABASE user_db;
CREATE DATABASE community_db;
CREATE DATABASE education_db;
CREATE DATABASE qna_db;
CREATE DATABASE event_db;
CREATE DATABASE notification_db;
