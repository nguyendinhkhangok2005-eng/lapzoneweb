function ShowDesc(){
    const mainImage = document.getElementById("desc_img_0");
    const thumbnails = document.querySelectorAll('.list_desc_img_product img');

    if (!mainImage || thumbnails.length === 0) {
        return;
    }

    let currentIndex = 0;
    let autoSlideTimer = null;
    const SLIDE_INTERVAL = 3000; // đổi ảnh mỗi 3 giây

    function goToSlide(index) {
        thumbnails.forEach(function(t) { t.classList.remove("active-thumb"); });
        currentIndex = (index + thumbnails.length) % thumbnails.length;
        mainImage.style.opacity = "0";
        setTimeout(function() {
            mainImage.src = thumbnails[currentIndex].src;
            mainImage.style.opacity = "1";
        }, 150);
        thumbnails[currentIndex].classList.add("active-thumb");
    }

    function startAutoSlide() {
        autoSlideTimer = setInterval(function() {
            goToSlide(currentIndex + 1);
        }, SLIDE_INTERVAL);
    }

    function resetAutoSlide() {
        clearInterval(autoSlideTimer);
        startAutoSlide();
    }

    // Click thumbnail: chuyển ảnh và reset bộ đếm tự động
    thumbnails.forEach(function(thumbnail, index) {
        thumbnail.addEventListener("click", function() {
            goToSlide(index);
            resetAutoSlide();
        });
    });

    // Khởi động: active thumbnail đầu tiên rồi bắt đầu tự chạy
    goToSlide(0);
    startAutoSlide();
}

function CountTime(){
    let endDate = new Date("2026-12-31T23:59:59").getTime();
    let countEl = document.getElementById("counttime");
    if (!countEl) return;

    let x = setInterval(function(){
        let now = new Date().getTime();
        let distance = endDate - now;

        if (distance < 0) {
            clearInterval(x);
            countEl.innerHTML = "Hết hạn";
            return;
        }

        let days    = Math.floor(distance / (1000 * 60 * 60 * 24));
        let hours   = Math.floor((distance % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
        let minutes = Math.floor((distance % (1000 * 60 * 60)) / (1000 * 60));
        let seconds = Math.floor((distance % (1000 * 60)) / 1000);
        countEl.innerHTML = days + " Ngày " + hours + " Giờ " + minutes + " Phút " + seconds + " Giây";
    }, 1000);
}

function totalPrice(){
    const ids = ["earphone", "keyboard", "mouse", "RAM", "monitor"];
    ids.forEach(function(id) {
        const el = document.getElementById(id);
        if (el) localStorage.setItem(id, el.checked);
    });
}

function attachCheckboxListeners() {
    const ids = ["earphone", "keyboard", "mouse", "RAM", "monitor"];
    ids.forEach(function(id) {
        const el = document.getElementById(id);
        if (el) el.addEventListener("change", totalPrice);
    });
}

function init(){
    ShowDesc();
    CountTime();
    totalPrice();
    attachCheckboxListeners();
}
window.onload = init;
