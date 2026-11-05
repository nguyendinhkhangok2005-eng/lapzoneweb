(() => {
    const slider = document.querySelector(".home-slider");
    if (!slider) {
        return;
    }

    const slides = Array.from(slider.querySelectorAll(".home-slide"));
    const dots = Array.from(slider.querySelectorAll("[data-slide-to]"));
    const previousButton = slider.querySelector("[data-slide-previous]");
    const nextButton = slider.querySelector("[data-slide-next]");
    const slideInterval = 5000;
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

    if (slides.length < 2 || dots.length !== slides.length || !previousButton || !nextButton) {
        return;
    }

    let activeIndex = slides.findIndex((slide) => slide.classList.contains("is-active"));
    let timerId;
    let isHovered = false;
    let isFocused = false;
    let pointerStartX = null;

    if (activeIndex < 0) {
        activeIndex = 0;
    }

    const clearTimer = () => {
        window.clearTimeout(timerId);
        timerId = undefined;
    };

    const scheduleNextSlide = () => {
        clearTimer();
        if (reducedMotion.matches || isHovered || isFocused) {
            return;
        }

        timerId = window.setTimeout(() => {
            showSlide(activeIndex + 1);
        }, slideInterval);
    };

    const showSlide = (index) => {
        activeIndex = (index + slides.length) % slides.length;

        slides.forEach((slide, slideIndex) => {
            const isActive = slideIndex === activeIndex;
            slide.classList.toggle("is-active", isActive);
            slide.setAttribute("aria-hidden", String(!isActive));
            dots[slideIndex].classList.toggle("is-active", isActive);

            if (isActive) {
                dots[slideIndex].setAttribute("aria-current", "true");
            } else {
                dots[slideIndex].removeAttribute("aria-current");
            }
        });

        scheduleNextSlide();
    };

    previousButton.addEventListener("click", () => showSlide(activeIndex - 1));
    nextButton.addEventListener("click", () => showSlide(activeIndex + 1));
    dots.forEach((dot, index) => {
        dot.addEventListener("click", () => showSlide(index));
    });

    slider.addEventListener("keydown", (event) => {
        if (event.key === "ArrowLeft") {
            event.preventDefault();
            showSlide(activeIndex - 1);
        } else if (event.key === "ArrowRight") {
            event.preventDefault();
            showSlide(activeIndex + 1);
        }
    });

    slider.addEventListener("mouseenter", () => {
        isHovered = true;
        clearTimer();
    });
    slider.addEventListener("mouseleave", () => {
        isHovered = false;
        scheduleNextSlide();
    });
    slider.addEventListener("focusin", () => {
        isFocused = true;
        clearTimer();
    });
    slider.addEventListener("focusout", (event) => {
        if (!slider.contains(event.relatedTarget)) {
            isFocused = false;
            scheduleNextSlide();
        }
    });

    const slidesContainer = slider.querySelector(".home-slider__slides");
    slidesContainer.addEventListener("pointerdown", (event) => {
        if (!event.isPrimary || event.button !== 0 || event.target.closest("a, button")) {
            return;
        }

        pointerStartX = event.clientX;
        slidesContainer.setPointerCapture(event.pointerId);
    });
    slidesContainer.addEventListener("pointerup", (event) => {
        if (pointerStartX === null) {
            return;
        }

        const distance = event.clientX - pointerStartX;
        pointerStartX = null;

        if (Math.abs(distance) >= 50) {
            showSlide(distance < 0 ? activeIndex + 1 : activeIndex - 1);
        }
    });
    slidesContainer.addEventListener("pointercancel", () => {
        pointerStartX = null;
    });

    reducedMotion.addEventListener("change", scheduleNextSlide);
    showSlide(activeIndex);
})();
