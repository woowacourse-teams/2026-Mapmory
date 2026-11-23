-- 만료된 refresh 토큰을 찾아 지울 때 쓴다. 없으면 삭제마다 표 전체를 훑어 회전과 잠금이 겹친다. (#454)
CREATE INDEX idx_refresh_expires ON refresh_token (expires_at);
