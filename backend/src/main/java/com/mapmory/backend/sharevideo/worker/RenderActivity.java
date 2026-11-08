package com.mapmory.backend.sharevideo.worker;

/**
 * 이 서버가 지금 공유 영상을 만들고 있는지 알려 준다. 같은 렌더러 서버에서 도는 썸네일 워커가 영상 작업이 끝날 때까지 쉬는 데 쓴다.
 * 영상(Remotion, Chromium)과 썸네일 변환이 2GB 메모리를 나눠 쓰므로 영상을 먼저 한다.
 */
public interface RenderActivity {

    boolean isRendering();
}
