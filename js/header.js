// Header scroll behavior (Hide on scroll down, show on scroll up)
let lastScrollTop = 0;
const header = document.querySelector(".header");

if (header) {
    window.addEventListener("scroll", function() {
        let currentScroll = window.scrollY || window.pageYOffset || document.documentElement.scrollTop;

        if (currentScroll > lastScrollTop && currentScroll > header.offsetHeight) {
            // Cuộn xuống -> Thêm class "hide"
            header.classList.add("hide");
        } else {
            // Cuộn lên -> Xóa class "hide"
            header.classList.remove("hide");
        }

        lastScrollTop = currentScroll <= 0 ? 0 : currentScroll;
    });
}

// Scrollspy behavior: Automatically highlight active navbar items on page scroll
document.addEventListener("DOMContentLoaded", () => {
    const sections = document.querySelectorAll("section[id]");
    const navLinks = document.querySelectorAll(".navbar-nav .nav-link");

    function scrollSpy() {
        let currentScroll = window.scrollY || window.pageYOffset || document.documentElement.scrollTop;
        
        // Add offset to account for header height
        const offset = 180; 

        sections.forEach(section => {
            const sectionTop = section.offsetTop - offset;
            const sectionHeight = section.offsetHeight;
            const sectionId = section.getAttribute("id");

            if (currentScroll >= sectionTop && currentScroll < sectionTop + sectionHeight) {
                navLinks.forEach(link => {
                    link.classList.remove("active");
                    if (link.getAttribute("href") === `#${sectionId}`) {
                        link.classList.add("active");
                    }
                });
            }
        });
    }

    // Toggle active immediately on click for instant response
    navLinks.forEach(link => {
        link.addEventListener("click", function() {
            navLinks.forEach(item => item.classList.remove("active"));
            this.classList.add("active");
        });
    });

    window.addEventListener("scroll", scrollSpy);
    window.addEventListener("resize", scrollSpy);
    // Initial run
    scrollSpy();
});