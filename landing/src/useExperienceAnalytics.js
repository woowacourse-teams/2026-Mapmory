import { useCallback, useEffect, useRef } from "react";
import { ANALYTICS_EVENTS, trackEvent } from "./analytics.js";

const VIEW_THRESHOLD = 0.5;
const VIEW_DURATION_MS = 1000;
const EXIT_GRACE_MS = 1500;
const OBSERVER_THRESHOLDS = [0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.75, 1];

function occupiesEnoughOfViewport(entry) {
  const referenceHeight = Math.min(entry.boundingClientRect.height, window.innerHeight);
  return entry.isIntersecting
    && entry.intersectionRect.height >= referenceHeight * VIEW_THRESHOLD;
}

function currentTime() {
  return typeof performance !== "undefined" ? performance.now() : Date.now();
}

export function useExperienceAnalytics(experienceType) {
  const sectionRef = useRef(null);
  const isVisibleRef = useRef(false);
  const hasViewedRef = useRef(false);
  const hasStartedRef = useRef(false);
  const hasBeenVisibleSinceStartRef = useRef(false);
  const hasEndedRef = useRef(false);
  const activeStartedAtRef = useRef(null);
  const activeDurationMsRef = useRef(0);
  const openedMemoryIdsRef = useRef(new Set());
  const addedMemoryIdsRef = useRef(new Set());
  const lastCompletedStepRef = useRef("experience_start");
  const viewTimerRef = useRef(null);
  const exitTimerRef = useRef(null);

  const clearViewTimer = useCallback(() => {
    if (!viewTimerRef.current) return;
    window.clearTimeout(viewTimerRef.current);
    viewTimerRef.current = null;
  }, []);

  const clearExitTimer = useCallback(() => {
    if (!exitTimerRef.current) return;
    window.clearTimeout(exitTimerRef.current);
    exitTimerRef.current = null;
  }, []);

  const resumeActiveTimer = useCallback(() => {
    if (
      !hasStartedRef.current
      || hasEndedRef.current
      || !isVisibleRef.current
      || document.hidden
      || activeStartedAtRef.current !== null
    ) return;
    activeStartedAtRef.current = currentTime();
  }, []);

  const pauseActiveTimer = useCallback(() => {
    if (activeStartedAtRef.current === null) return;
    activeDurationMsRef.current += currentTime() - activeStartedAtRef.current;
    activeStartedAtRef.current = null;
  }, []);

  const getActiveDurationMs = useCallback(() => {
    const inProgress = activeStartedAtRef.current === null
      ? 0
      : currentTime() - activeStartedAtRef.current;
    return Math.max(0, Math.round(activeDurationMsRef.current + inProgress));
  }, []);

  const endExperience = useCallback((exitReason, transportType) => {
    if (
      hasEndedRef.current
      || !hasStartedRef.current
      || !hasBeenVisibleSinceStartRef.current
    ) return false;

    pauseActiveTimer();
    clearExitTimer();
    hasEndedRef.current = true;
    return trackEvent(ANALYTICS_EVENTS.EXPERIENCE_END, {
      experience_type: experienceType,
      active_duration_ms: getActiveDurationMs(),
      unique_memories_opened: openedMemoryIdsRef.current.size,
      last_completed_step: lastCompletedStepRef.current,
      exit_reason: exitReason,
      transport_type: transportType,
    });
  }, [clearExitTimer, experienceType, getActiveDurationMs, pauseActiveTimer]);

  const markViewed = useCallback(() => {
    if (hasViewedRef.current || document.hidden) return;
    clearViewTimer();
    hasViewedRef.current = true;
    trackEvent(ANALYTICS_EVENTS.EXPERIENCE_VIEW, { experience_type: experienceType });
  }, [clearViewTimer, experienceType]);

  const scheduleView = useCallback(() => {
    if (document.hidden || !isVisibleRef.current || hasViewedRef.current || viewTimerRef.current) return;
    viewTimerRef.current = window.setTimeout(() => {
      viewTimerRef.current = null;
      if (isVisibleRef.current) markViewed();
    }, VIEW_DURATION_MS);
  }, [markViewed]);

  useEffect(() => {
    const section = sectionRef.current;
    if (!section) return undefined;

    const observer = new IntersectionObserver(([entry]) => {
      isVisibleRef.current = occupiesEnoughOfViewport(entry);
      if (!isVisibleRef.current) {
        pauseActiveTimer();
        clearViewTimer();
        if (
          hasStartedRef.current
          && hasBeenVisibleSinceStartRef.current
          && !hasEndedRef.current
          && !exitTimerRef.current
        ) {
          exitTimerRef.current = window.setTimeout(() => {
            exitTimerRef.current = null;
            endExperience("section_exit");
          }, EXIT_GRACE_MS);
        }
        return;
      }

      clearExitTimer();
      if (hasStartedRef.current && !hasEndedRef.current) {
        hasBeenVisibleSinceStartRef.current = true;
        resumeActiveTimer();
      }

      scheduleView();
    }, { threshold: OBSERVER_THRESHOLDS });

    observer.observe(section);
    return () => {
      clearViewTimer();
      clearExitTimer();
      pauseActiveTimer();
      observer.disconnect();
    };
  }, [clearExitTimer, clearViewTimer, endExperience, pauseActiveTimer, resumeActiveTimer, scheduleView]);

  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.hidden) { pauseActiveTimer(); clearViewTimer(); }
      else { resumeActiveTimer(); scheduleView(); }
    };
    const handlePageHide = () => endExperience("page_hide", "beacon");

    document.addEventListener("visibilitychange", handleVisibilityChange);
    window.addEventListener("pagehide", handlePageHide);
    return () => {
      document.removeEventListener("visibilitychange", handleVisibilityChange);
      window.removeEventListener("pagehide", handlePageHide);
    };
  }, [clearViewTimer, endExperience, pauseActiveTimer, resumeActiveTimer, scheduleView]);

  const trackEntryClick = useCallback((placement) => {
    trackEvent(ANALYTICS_EVENTS.EXPERIENCE_CTA_CLICK, {
      experience_type: experienceType,
      cta_placement: placement,
    });
  }, [experienceType]);

  const startExperience = useCallback((interactionType) => {
    if (hasStartedRef.current || hasEndedRef.current) return;
    // A deliberate interaction proves exposure even before the passive 1s threshold.
    markViewed();
    hasStartedRef.current = true;
    lastCompletedStepRef.current = "experience_start";
    trackEvent(ANALYTICS_EVENTS.EXPERIENCE_START, {
      experience_type: experienceType,
      interaction_type: interactionType,
    });
    if (isVisibleRef.current) {
      hasBeenVisibleSinceStartRef.current = true;
      resumeActiveTimer();
    }
  }, [experienceType, markViewed, resumeActiveTimer]);

  const trackMemoryOpen = useCallback((memoryId, selectionSource) => {
    startExperience("place_select");
    if (hasEndedRef.current || openedMemoryIdsRef.current.has(memoryId)) return;

    openedMemoryIdsRef.current.add(memoryId);
    lastCompletedStepRef.current = "memory_open";
    trackEvent(ANALYTICS_EVENTS.MEMORY_OPEN, {
      experience_type: experienceType,
      memory_id: memoryId,
      selection_source: selectionSource,
      open_index: openedMemoryIdsRef.current.size,
      time_since_start_ms: getActiveDurationMs(),
    });
  }, [experienceType, getActiveDurationMs, startExperience]);

  const trackMemoryAdd = useCallback((memoryId) => {
    startExperience("memory_add");
    if (hasEndedRef.current || addedMemoryIdsRef.current.has(memoryId)) return;

    addedMemoryIdsRef.current.add(memoryId);
    lastCompletedStepRef.current = "korea_memory_add";
    trackEvent(ANALYTICS_EVENTS.KOREA_MEMORY_ADD, {
      experience_type: experienceType,
      memory_id: memoryId,
      add_index: addedMemoryIdsRef.current.size,
      time_since_start_ms: getActiveDurationMs(),
    });
  }, [experienceType, getActiveDurationMs, startExperience]);

  return {
    sectionRef,
    trackEntryClick,
    startExperience,
    trackMemoryOpen,
    trackMemoryAdd,
    endExperience,
  };
}
