const reveals = document.querySelectorAll('.reveal');
const faqButtons = document.querySelectorAll('[data-faq]');
const mobileMenu = document.querySelector('.mobile-menu');
const mobileMenuSummary = mobileMenu?.querySelector('summary');
const mobileMenuLinks = mobileMenu?.querySelectorAll('a') ?? [];

if ('IntersectionObserver' in window) {
  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('in-view');
        }
      });
    },
    {
      threshold: 0.16,
      rootMargin: '0px 0px -60px 0px',
    }
  );

  reveals.forEach((el) => observer.observe(el));
} else {
  reveals.forEach((el) => el.classList.add('in-view'));
}

if (mobileMenu && mobileMenuSummary) {
  mobileMenuSummary.setAttribute('aria-expanded', String(mobileMenu.hasAttribute('open')));

  mobileMenu.addEventListener('toggle', () => {
    mobileMenuSummary.setAttribute('aria-expanded', String(mobileMenu.hasAttribute('open')));
  });

  mobileMenuLinks.forEach((link) => {
    link.addEventListener('click', () => {
      mobileMenu.removeAttribute('open');
    });
  });

  document.addEventListener('click', (event) => {
    if (!mobileMenu.contains(event.target)) {
      mobileMenu.removeAttribute('open');
    }
  });
}

faqButtons.forEach((button) => {
  button.addEventListener('click', () => {
    const answer = button.nextElementSibling;
    const icon = button.querySelector('strong');
    const isOpen = answer.classList.contains('open');

    document.querySelectorAll('.faq-answer.open').forEach((panel) => {
      if (panel !== answer) {
        panel.classList.remove('open');
        panel.setAttribute('aria-hidden', 'true');
        const prevButton = panel.previousElementSibling;
        const prevIcon = prevButton?.querySelector('strong');
        if (prevIcon) prevIcon.textContent = '+';
        if (prevButton) prevButton.setAttribute('aria-expanded', 'false');
      }
    });

    answer.classList.toggle('open', !isOpen);
    answer.setAttribute('aria-hidden', String(isOpen));
    icon.textContent = isOpen ? '+' : '−';
    button.setAttribute('aria-expanded', String(!isOpen));
  });
});
