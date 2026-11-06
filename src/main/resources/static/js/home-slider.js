(() => {
    const slider = document.querySelector(".home-slider");
    if (!slider) {
        return;
    }

    const showcase = slider.closest(".home-showcase");
    const track = slider.querySelector(".home-slider__slides");
    const slides = Array.from(track.querySelectorAll(".home-slide"));
    const dots = Array.from(slider.querySelectorAll("[data-slide-to]"));
    const thumbnails = Array.from(document.querySelectorAll("[data-thumbnail-to]"));
    const thumbnailStrip = showcase ? showcase.querySelector(".home-thumbnails") : null;
    const previousButton = slider.querySelector("[data-slide-previous]");
    const nextButton = slider.querySelector("[data-slide-next]");
    const progress = slider.querySelector(".home-slider__progress-fill");
    const slideInterval = 5000;
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

    if (slides.length < 2 || dots.length !== slides.length || thumbnails.length !== slides.length ||
        !previousButton || !nextButton || !progress || !showcase || !thumbnailStrip) {
        return;
    }

    const modulo = (value, length) => ((value % length) + length) % length;
    let activeIndex = Math.max(0, slides.findIndex((slide) => slide.classList.contains("is-active")));
    let trackIndex = activeIndex + 1;
    let timerId = null;
    let deadline = 0;
    let remainingTime = slideInterval;
    let isHovered = false;
    let isFocused = false;
    let isDragging = false;
    let pointerStart = null;
    let suppressSlideClickUntil = 0;
    let thumbnailDrag = null;
    let suppressThumbnailClickUntil = 0;

    const setTrackPosition = (position) => {
        track.style.transform = `translate3d(${-position * slider.clientWidth}px, 0, 0)`;
        trackIndex = position;
        Array.from(track.children).forEach((slide, slideIndex) => {
            const offset = reducedMotion.matches ? 0 : (position - slideIndex) * slider.clientWidth;
            const product = slide.querySelector(".home-slide__product");
            const backdrop = slide.querySelector(".home-slide__backdrop");
            if (product && backdrop) {
                product.style.setProperty("--parallax-x", `${offset * 0.035}px`);
                product.style.setProperty("--parallax-y", `${Math.abs(offset) * 0.006}px`);
                backdrop.style.setProperty("--parallax-bg-x", `${offset * 0.06}px`);
            }
        });
    };

    const isPaused = () => isHovered || isFocused || isDragging || document.hidden || reducedMotion.matches;

    const restartProgress = () => {
        progress.classList.remove("is-running");
        void progress.offsetWidth;
        if (!reducedMotion.matches) {
            progress.classList.add("is-running");
        }
    };

    const clearTimer = () => {
        if (timerId !== null) {
            window.clearTimeout(timerId);
            timerId = null;
        }
    };

    const resetAutoplay = () => {
        clearTimer();
        remainingTime = slideInterval;
    };

    const updatePlayback = () => {
        if (isPaused()) {
            if (timerId !== null) {
                clearTimer();
                remainingTime = Math.max(0, deadline - Date.now());
            }
            slider.classList.add("is-paused");
            return;
        }

        slider.classList.remove("is-paused");
        if (timerId === null) {
            deadline = Date.now() + remainingTime;
            timerId = window.setTimeout(() => {
                timerId = null;
                remainingTime = slideInterval;
                navigate(activeIndex + 1);
            }, remainingTime);
        }
    };

    const normalizeTrack = (position) => {
        track.style.transition = "none";
        setTrackPosition(position);
        void track.offsetWidth;
        track.style.transition = "";
    };

    const updateActiveState = () => {
        slides.forEach((slide, index) => {
            const isActive = index === activeIndex;
            slide.classList.toggle("is-active", isActive);
            slide.setAttribute("aria-hidden", String(!isActive));
            slide.inert = !isActive;
            dots[index].classList.toggle("is-active", isActive);
            thumbnails[index].classList.toggle("is-active", isActive);
            if (isActive) {
                dots[index].setAttribute("aria-current", "true");
                thumbnails[index].setAttribute("aria-current", "true");
            } else {
                dots[index].removeAttribute("aria-current");
                thumbnails[index].removeAttribute("aria-current");
            }
        });
        ensureThumbnailVisible(activeIndex);
    };

    const navigate = (requestedIndex) => {
        resetAutoplay();
        const nextIndex = modulo(requestedIndex, slides.length);
        const isNextLoop = !reducedMotion.matches && requestedIndex >= slides.length && nextIndex === 0;
        const isPreviousLoop = !reducedMotion.matches && requestedIndex < 0 && nextIndex === slides.length - 1;

        if (nextIndex === activeIndex) {
            restartProgress();
            updatePlayback();
            return;
        }

        activeIndex = nextIndex;
        updateActiveState();
        trackIndex = isNextLoop ? slides.length + 1 : (isPreviousLoop ? 0 : nextIndex + 1);
        setTrackPosition(trackIndex);
        restartProgress();
        updatePlayback();
    };

    const ensureThumbnailVisible = (index) => {
        const thumbnail = thumbnails[index];
        if (!thumbnail) {
            return;
        }

        const stripRect = thumbnailStrip.getBoundingClientRect();
        const thumbRect = thumbnail.getBoundingClientRect();
        const horizontal = window.matchMedia("(max-width: 767px)").matches;
        let target = null;
        if (horizontal && thumbRect.left < stripRect.left) {
            target = { left: thumbnailStrip.scrollLeft - (stripRect.left - thumbRect.left) };
        } else if (horizontal && thumbRect.right > stripRect.right) {
            target = { left: thumbnailStrip.scrollLeft + thumbRect.right - stripRect.right };
        } else if (!horizontal && thumbRect.top < stripRect.top) {
            target = { top: thumbnailStrip.scrollTop - (stripRect.top - thumbRect.top) };
        } else if (!horizontal && thumbRect.bottom > stripRect.bottom) {
            target = { top: thumbnailStrip.scrollTop + thumbRect.bottom - stripRect.bottom };
        }

        if (target) {
            thumbnailStrip.scrollTo({
                ...target,
                behavior: reducedMotion.matches ? "auto" : "smooth"
            });
        }
    };

    const setImageSizeLimit = (image) => {
        const updateLimit = () => {
            if (!image.naturalWidth || !image.naturalHeight) {
                return;
            }
            image.style.setProperty("--image-max-width", `${image.naturalWidth * 1.19}px`);
            image.style.setProperty("--image-max-height", `${image.naturalHeight * 1.19}px`);
        };
        image.addEventListener("load", updateLimit);
        if (image.complete) {
            updateLimit();
        }
    };

    slides.forEach((slide) => {
        const image = slide.querySelector(".home-slide__image");
        setImageSizeLimit(image);
    });

    const firstClone = slides[0].cloneNode(true);
    const lastClone = slides[slides.length - 1].cloneNode(true);
    [firstClone, lastClone].forEach((clone) => {
        clone.classList.add("is-clone");
        clone.classList.remove("is-active");
        clone.setAttribute("aria-hidden", "true");
        clone.inert = true;
        setImageSizeLimit(clone.querySelector(".home-slide__image"));
    });
    track.prepend(lastClone);
    track.append(firstClone);

    updateActiveState();
    track.style.transition = "none";
    setTrackPosition(trackIndex);
    void track.offsetWidth;
    restartProgress();
    window.requestAnimationFrame(() => {
        track.style.transition = "";
    });

    track.addEventListener("transitionend", (event) => {
        if (event.target !== track || event.propertyName !== "transform") {
            return;
        }
        if (trackIndex === 0) {
            normalizeTrack(slides.length);
        } else if (trackIndex === slides.length + 1) {
            normalizeTrack(1);
        }
    });

    previousButton.addEventListener("click", () => navigate(activeIndex - 1));
    nextButton.addEventListener("click", () => navigate(activeIndex + 1));
    dots.forEach((dot, index) => dot.addEventListener("click", () => navigate(index)));
    thumbnails.forEach((thumbnail, index) => thumbnail.addEventListener("click", () => navigate(index)));

    slider.addEventListener("keydown", (event) => {
        if (event.key === "ArrowLeft") {
            event.preventDefault();
            navigate(activeIndex - 1);
        } else if (event.key === "ArrowRight") {
            event.preventDefault();
            navigate(activeIndex + 1);
        }
    });

    showcase.addEventListener("mouseenter", () => {
        isHovered = true;
        updatePlayback();
    });
    showcase.addEventListener("mouseleave", () => {
        isHovered = false;
        updatePlayback();
    });
    showcase.addEventListener("focusin", () => {
        isFocused = true;
        updatePlayback();
    });
    showcase.addEventListener("focusout", (event) => {
        if (!showcase.contains(event.relatedTarget)) {
            isFocused = false;
            updatePlayback();
        }
    });

    track.addEventListener("pointerdown", (event) => {
        if (!event.isPrimary || (event.pointerType === "mouse" && event.button !== 0)) {
            return;
        }
        pointerStart = {
            pointerId: event.pointerId,
            startX: event.clientX,
            lastX: event.clientX,
            lastTime: performance.now(),
            velocity: 0,
            startPosition: trackIndex
        };
    });

    track.addEventListener("pointermove", (event) => {
        if (!pointerStart || event.pointerId !== pointerStart.pointerId) {
            return;
        }
        const now = performance.now();
        const elapsed = Math.max(1, now - pointerStart.lastTime);
        pointerStart.velocity = (event.clientX - pointerStart.lastX) / elapsed;
        pointerStart.lastX = event.clientX;
        pointerStart.lastTime = now;
        const distance = event.clientX - pointerStart.startX;

        if (!isDragging && Math.abs(distance) > 5) {
            isDragging = true;
            slider.classList.add("is-dragging");
            track.setPointerCapture(event.pointerId);
            updatePlayback();
        }
        if (isDragging) {
            event.preventDefault();
            setTrackPosition(pointerStart.startPosition - distance / slider.clientWidth);
        }
    });

    const finishSlideDrag = (event, allowNavigation) => {
        if (!pointerStart || (event && event.pointerId !== pointerStart.pointerId)) {
            return;
        }
        const drag = pointerStart;
        const wasDragging = isDragging;
        pointerStart = null;
        isDragging = false;
        slider.classList.remove("is-dragging");
        if (wasDragging && allowNavigation) {
            suppressSlideClickUntil = Date.now() + 400;
        }

        if (event && track.hasPointerCapture(drag.pointerId)) {
            track.releasePointerCapture(drag.pointerId);
        }

        if (wasDragging && allowNavigation) {
            const distance = drag.startX - drag.lastX;
            const projectedDistance = distance - drag.velocity * 110;
            if (Math.abs(projectedDistance) >= slider.clientWidth * 0.15) {
                navigate(activeIndex + (projectedDistance > 0 ? 1 : -1));
            } else {
                setTrackPosition(activeIndex + 1);
                updatePlayback();
            }
        } else {
            setTrackPosition(activeIndex + 1);
            updatePlayback();
        }
    };

    track.addEventListener("pointerup", (event) => finishSlideDrag(event, true));
    track.addEventListener("pointercancel", (event) => finishSlideDrag(event, false));
    window.addEventListener("pointerup", (event) => finishSlideDrag(event, true));
    window.addEventListener("pointercancel", (event) => finishSlideDrag(event, false));
    track.addEventListener("lostpointercapture", (event) => {
        if (pointerStart) {
            finishSlideDrag(event, false);
        }
    });

    slider.addEventListener("click", (event) => {
        if (Date.now() < suppressSlideClickUntil && event.target.closest(".home-slide")) {
            event.preventDefault();
            event.stopPropagation();
            suppressSlideClickUntil = 0;
        }
    }, true);
    slider.addEventListener("dragstart", (event) => event.preventDefault());

    thumbnailStrip.addEventListener("pointerdown", (event) => {
        if (!event.isPrimary || event.pointerType === "touch" || (event.pointerType === "mouse" && event.button !== 0)) {
            return;
        }
        thumbnailDrag = {
            pointerId: event.pointerId,
            startX: event.clientX,
            startY: event.clientY,
            startLeft: thumbnailStrip.scrollLeft,
            startTop: thumbnailStrip.scrollTop,
            moved: false
        };
        thumbnailStrip.setPointerCapture(event.pointerId);
    });

    thumbnailStrip.addEventListener("pointermove", (event) => {
        if (!thumbnailDrag || event.pointerId !== thumbnailDrag.pointerId) {
            return;
        }
        const horizontal = window.matchMedia("(max-width: 767px)").matches;
        const delta = horizontal ? event.clientX - thumbnailDrag.startX : event.clientY - thumbnailDrag.startY;
        if (Math.abs(delta) > 5) {
            if (!thumbnailDrag.moved) {
                thumbnailStrip.setPointerCapture(event.pointerId);
            }
            thumbnailDrag.moved = true;
            thumbnailStrip.classList.add("is-dragging");
            event.preventDefault();
            if (horizontal) {
                thumbnailStrip.scrollLeft = thumbnailDrag.startLeft - delta;
            } else {
                thumbnailStrip.scrollTop = thumbnailDrag.startTop - delta;
            }
        }
    });

    const finishThumbnailDrag = (event) => {
        if (!thumbnailDrag || (event && event.pointerId !== thumbnailDrag.pointerId)) {
            return;
        }
        const drag = thumbnailDrag;
        thumbnailDrag = null;
        thumbnailStrip.classList.remove("is-dragging");
        if (drag.moved) {
            suppressThumbnailClickUntil = Date.now() + 400;
        }
        if (event && thumbnailStrip.hasPointerCapture(drag.pointerId)) {
            thumbnailStrip.releasePointerCapture(drag.pointerId);
        }
    };

    thumbnailStrip.addEventListener("pointerup", finishThumbnailDrag);
    thumbnailStrip.addEventListener("pointercancel", finishThumbnailDrag);
    thumbnailStrip.addEventListener("lostpointercapture", (event) => {
        if (thumbnailDrag) {
            finishThumbnailDrag(event);
        }
    });
    thumbnailStrip.addEventListener("click", (event) => {
        if (Date.now() < suppressThumbnailClickUntil) {
            event.preventDefault();
            event.stopPropagation();
            suppressThumbnailClickUntil = 0;
        }
    }, true);
    thumbnailStrip.addEventListener("wheel", (event) => {
        const horizontal = window.matchMedia("(max-width: 767px)").matches;
        const isOverflowing = horizontal
            ? thumbnailStrip.scrollWidth > thumbnailStrip.clientWidth + 1
            : thumbnailStrip.scrollHeight > thumbnailStrip.clientHeight + 1;
        if (!isOverflowing) {
            return;
        }
        event.preventDefault();
        if (horizontal) {
            thumbnailStrip.scrollLeft += event.deltaY || event.deltaX;
        } else {
            thumbnailStrip.scrollTop += event.deltaY || event.deltaX;
        }
    }, { passive: false });

    window.addEventListener("resize", () => setTrackPosition(trackIndex));
    document.addEventListener("visibilitychange", updatePlayback);
    reducedMotion.addEventListener("change", () => {
        resetAutoplay();
        restartProgress();
        if (reducedMotion.matches && (trackIndex === 0 || trackIndex === slides.length + 1)) {
            normalizeTrack(activeIndex + 1);
        } else {
            setTrackPosition(trackIndex);
        }
        updatePlayback();
    });

    updatePlayback();
})();
