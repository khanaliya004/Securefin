// script.js

// Reveal Animation

const cards = document.querySelectorAll(
  ".skill-card, .project-card, .about-box, .contact-box, .experience-card"
);

window.addEventListener("scroll", reveal);

function reveal() {

  cards.forEach((card) => {

    const windowHeight = window.innerHeight;
    const revealTop = card.getBoundingClientRect().top;

    if (revealTop < windowHeight - 100) {

      card.style.opacity = "1";
      card.style.transform = "translateY(0)";

    }

  });

}

cards.forEach((card) => {

  card.style.opacity = "0";
  card.styletransform = "translateY(40px)";
  card.style.transition = "0.6s ease";

});