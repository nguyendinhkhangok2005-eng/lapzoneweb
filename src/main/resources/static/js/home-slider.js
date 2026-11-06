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
    const thumbnailStrip = showcase?.querySelector(".home-thumbnails");
    const previousButton = slider.querySelector("[data-slide-previous]");
    const nextButton = slider.querySelector("[data-slide-next]");
    const autoplayButton = slider.querySelector("[data-autoplay-toggle]");
    const counter = slider.querySelector("[data-slide-counter]");
    const progress = slider.querySelector(".home-slider__progress-fill");
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const slideInterval = 5000;

    const setImageSizeLimit = (image, widthProperty, heightProperty) => {
        if (!image) {
            return;
        }
        const updateLimit = () => {
            if (!image.naturalWidth || !image.naturalHeight) {
                return;
            }
            image.style.setProperty(widthProperty, `${image.naturalWidth * 1.25}px`);
            image.style.setProperty(heightProperty, `${image.naturalHeight * 1.25}px`);
        };
        image.addEventListener("load", updateLimit);
        if (image.complete) {
            updateLimit();
        }
    };

    const setupImageMedia = (root) => {
        root.querySelectorAll(".home-slide__image-frame, .home-thumbnail__media").forEach((media) => {
            const cutout = media.querySelector("[data-cutout-image]");
            const fallback = media.querySelector("[data-fallback-src]");
            const plate = media.querySelector(".home-slide__plate");
            if (!fallback) {
                return;
            }

            const showFallback = () => {
                if (cutout) {
                    cutout.hidden = true;
                    cutout.removeAttribute("src");
                }
                fallback.src = fallback.dataset.fallbackSrc;
                fallback.hidden = false;
                if (plate) {
                    plate.hidden = false;
                }
            };

            setImageSizeLimit(cutout, "--image-max-width", "--image-max-height");
            setImageSizeLimit(fallback, "--fallback-max-width", "--fallback-max-height");
            if (cutout) {
                cutout.addEventListener("error", showFallback, { once: true });
                if (cutout.complete && cutout.naturalWidth === 0) {
                    showFallback();
                }
            } else {
                showFallback();
            }
        });
    };

    setupImageMedia(slider);
    thumbnails.forEach(setupImageMedia);

    if (slides.length < 2 || dots.length !== slides.length || thumbnails.length !== slides.length ||
        !showcase || !thumbnailStrip || !previousButton || !nextButton || !autoplayButton || !counter || !progress) {
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
    let userPaused = false;
    let pointerStart = null;
    let pointerFrame = 0;
    let tiltFrame = 0;
    let thumbnailDrag = null;
    let thumbnailFrame = 0;
    let suppressClick = false;
    let latestPointer = null;
    let latestThumbnailPointer = null;

    const setLayerOffsets = (position) => {
        Array.from(track.children).forEach((slide, index) => {
            const offset = reducedMotion.matches ? 0 : (position - index) * slider.clientWidth;
            const image = slide.querySelector(".home-slide__image");
            const details = slide.querySelector(".home-slide__details");
            const visual = slide.querySelector(".home-slide__visual");
            if (image && details && visual) {
                image.style.setProperty("--parallax-image-x", `${-offset * 0.035}px`);
                image.style.setProperty("--parallax-image-y", `${Math.abs(offset) * 0.006}px`);
                details.style.setProperty("--parallax-copy-x", `${offset * 0.012}px`);
                visual.style.setProperty("--parallax-blob-x", `${offset * 0.02}px`);
            }
        });
    };

    const setTrackPosition = (position) => {
        trackIndex = position;
        track.style.transform = `translate3d(${-position * slider.clientWidth}px, 0, 0)`;
        setLayerOffsets(position);
    };

    const isPaused = () => isHovered || isFocused || isDragging || userPaused || document.hidden;

    const restartProgress = () => {
        progress.classList.remove("is-running");
        void progress.offsetWidth;
        progress.classList.add("is-running");
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

    const ensureThumbnailVisible = (index) => {
        const thumbnail = thumbnails[index];
        if (!thumbnail) {
            return;
        }
        const railRect = thumbnailStrip.getBoundingClientRect();
        const itemRect = thumbnail.getBoundingClientRect();
        let targetLeft = thumbnailStrip.scrollLeft;
        if (itemRect.left < railRect.left) {
            targetLeft -= railRect.left - itemRect.left;
        } else if (itemRect.right > railRect.right) {
            targetLeft += itemRect.right - railRect.right;
        }
        if (targetLeft !== thumbnailStrip.scrollLeft) {
            thumbnailStrip.scrollTo({
                left: targetLeft,
                behavior: reducedMotion.matches ? "auto" : "smooth"
            });
        }
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
        counter.textContent = `${activeIndex + 1} / ${slides.length}`;
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

    const firstClone = slides[0].cloneNode(true);
    const lastClone = slides[slides.length - 1].cloneNode(true);
    [firstClone, lastClone].forEach((clone) => {
        clone.classList.add("is-clone");
        clone.classList.remove("is-active");
        clone.setAttribute("aria-hidden", "true");
        clone.inert = true;
        setupImageMedia(clone);
    });
    track.prepend(lastClone);
    track.append(firstClone);

    if (reducedMotion.matches) {
        slider.classList.add("is-reduced-motion");
    }
    updateActiveState();
    track.style.transition = "none";
    setTrackPosition(trackIndex);
    void track.offsetWidth;
    track.style.transition = "";
    restartProgress();

    track.addEventListener("transitionend", (event) => {
        if (event.target !== track || event.propertyName !== "transform" || reducedMotion.matches) {
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

    autoplayButton.addEventListener("click", () => {
        userPaused = !userPaused;
        autoplayButton.setAttribute("aria-pressed", String(userPaused));
        autoplayButton.setAttribute("aria-label", userPaused ? "Phát tự chuyển" : "Tạm dừng tự chuyển");
        if (userPaused) {
            resetAutoplay();
        } else {
            remainingTime = slideInterval;
        }
        restartProgress();
        updatePlayback();
    });

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

    const applyPointerPosition = () => {
        pointerFrame = 0;
        if (!pointerStart || !latestPointer) {
            return;
        }
        const distance = latestPointer.clientX - pointerStart.startX;
        setTrackPosition(pointerStart.startTrackIndex - distance / slider.clientWidth);
    };

    const resetTilt = () => {
        const activeVisual = slides[activeIndex].querySelector(".home-slide__visual");
        if (activeVisual) {
            activeVisual.style.setProperty("--tilt-x", "0deg");
            activeVisual.style.setProperty("--tilt-y", "0deg");
        }
    };

    const applyTilt = () => {
        tiltFrame = 0;
        if (!latestPointer || reducedMotion.matches || isDragging || latestPointer.pointerType !== "mouse") {
            return;
        }
        const rect = slider.getBoundingClientRect();
        const horizontal = Math.max(-1, Math.min(1, (latestPointer.clientX - rect.left - rect.width / 2) / (rect.width / 2)));
        const vertical = Math.max(-1, Math.min(1, (latestPointer.clientY - rect.top - rect.height / 2) / (rect.height / 2)));
        const activeVisual = slides[activeIndex].querySelector(".home-slide__visual");
        if (activeVisual) {
            activeVisual.style.setProperty("--tilt-y", `${horizontal * 6}deg`);
            activeVisual.style.setProperty("--tilt-x", `${vertical * -4}deg`);
        }
    };

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
            startTrackIndex: trackIndex
        };
        latestPointer = event;
    });

    track.addEventListener("pointermove", (event) => {
        if (pointerStart && event.pointerId === pointerStart.pointerId) {
            const now = performance.now();
            const elapsed = Math.max(1, now - pointerStart.lastTime);
            pointerStart.velocity = (event.clientX - pointerStart.lastX) / elapsed;
            pointerStart.lastX = event.clientX;
            pointerStart.lastTime = now;
            latestPointer = event;

            if (!isDragging && Math.abs(event.clientX - pointerStart.startX) > 5) {
                isDragging = true;
                slider.classList.add("is-dragging");
                track.setPointerCapture(event.pointerId);
                resetTilt();
                updatePlayback();
            }
            if (isDragging) {
                event.preventDefault();
                if (!pointerFrame) {
                    pointerFrame = window.requestAnimationFrame(applyPointerPosition);
                }
            }
        } else if (event.pointerType === "mouse") {
            latestPointer = event;
            if (!tiltFrame) {
                tiltFrame = window.requestAnimationFrame(applyTilt);
            }
        }
    });

    const finishSlideDrag = (event, shouldNavigate) => {
        if (!pointerStart || (event && event.pointerId !== pointerStart.pointerId)) {
            return;
        }
        const drag = pointerStart;
        const wasDragging = isDragging;
        pointerStart = null;
        latestPointer = null;
        isDragging = false;
        slider.classList.remove("is-dragging");
        if (pointerFrame) {
            window.cancelAnimationFrame(pointerFrame);
            pointerFrame = 0;
            setTrackPosition(drag.startTrackIndex - (drag.lastX - drag.startX) / slider.clientWidth);
        }
        if (wasDragging) {
            suppressClick = true;
            window.setTimeout(() => {
                suppressClick = false;
            }, 0);
        }
        if (event && track.hasPointerCapture(drag.pointerId)) {
            track.releasePointerCapture(drag.pointerId);
        }

        if (wasDragging && shouldNavigate) {
            const distance = drag.startX - drag.lastX;
            const projectedDistance = distance - drag.velocity * 140;
            if (Math.abs(projectedDistance) >= slider.clientWidth * .15) {
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
        if (suppressClick && event.target.closest("a, button")) {
            event.preventDefault();
            event.stopPropagation();
            suppressClick = false;
        }
    }, true);
    slider.addEventListener("dragstart", (event) => event.preventDefault());

    slider.addEventListener("pointermove", (event) => {
        if (event.pointerType === "mouse" && !pointerStart) {
            latestPointer = event;
            if (!tiltFrame) {
                tiltFrame = window.requestAnimationFrame(applyTilt);
            }
        }
    });
    slider.addEventListener("pointerleave", () => {
        latestPointer = null;
        resetTilt();
    });

    const applyThumbnailDrag = () => {
        thumbnailFrame = 0;
        if (!thumbnailDrag || !latestThumbnailPointer) {
            return;
        }
        thumbnailStrip.scrollLeft = thumbnailDrag.startScrollLeft - (latestThumbnailPointer.clientX - thumbnailDrag.startX);
    };

    thumbnailStrip.addEventListener("pointerdown", (event) => {
        if (!event.isPrimary || (event.pointerType === "mouse" && event.button !== 0)) {
            return;
        }
        thumbnailDrag = {
            pointerId: event.pointerId,
            startX: event.clientX,
            startScrollLeft: thumbnailStrip.scrollLeft,
            moved: false
        };
        latestThumbnailPointer = event;
    });

    thumbnailStrip.addEventListener("pointermove", (event) => {
        if (!thumbnailDrag || event.pointerId !== thumbnailDrag.pointerId) {
            return;
        }
        latestThumbnailPointer = event;
        if (!thumbnailDrag.moved && Math.abs(event.clientX - thumbnailDrag.startX) > 5) {
            thumbnailDrag.moved = true;
            thumbnailStrip.classList.add("is-dragging");
            thumbnailStrip.setPointerCapture(event.pointerId);
        }
        if (thumbnailDrag.moved) {
            event.preventDefault();
            if (!thumbnailFrame) {
                thumbnailFrame = window.requestAnimationFrame(applyThumbnailDrag);
            }
        }
    });

    const finishThumbnailDrag = (event) => {
        if (!thumbnailDrag || (event && event.pointerId !== thumbnailDrag.pointerId)) {
            return;
        }
        const drag = thumbnailDrag;
        thumbnailDrag = null;
        latestThumbnailPointer = null;
        thumbnailStrip.classList.remove("is-dragging");
        if (thumbnailFrame) {
            window.cancelAnimationFrame(thumbnailFrame);
            thumbnailFrame = 0;
            thumbnailStrip.scrollLeft = drag.startScrollLeft - ((event?.clientX ?? drag.startX) - drag.startX);
        }
        if (drag.moved) {
            suppressClick = true;
            window.setTimeout(() => {
                suppressClick = false;
            }, 0);
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
        if (suppressClick) {
            event.preventDefault();
            event.stopPropagation();
            suppressClick = false;
        }
    }, true);

    window.addEventListener("resize", () => setTrackPosition(trackIndex));
    document.addEventListener("visibilitychange", updatePlayback);
    reducedMotion.addEventListener("change", () => {
        resetAutoplay();
        restartProgress();
        if (reducedMotion.matches) {
            slider.classList.add("is-reduced-motion");
        } else {
            slider.classList.remove("is-reduced-motion");
        }
        normalizeTrack(activeIndex + 1);
        updatePlayback();
    });

    updatePlayback();
})();
