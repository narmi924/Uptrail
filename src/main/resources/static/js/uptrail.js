/*
 * Shared browser helpers: CSRF-aware JSON requests, navigation toggle, confirmation prompts and
 * page-size selectors. No framework; everything degrades to plain links and forms without JavaScript.
 */
(function () {
  'use strict';

  function csrfHeaders() {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    var headers = {};
    if (token && header) {
      headers[header.getAttribute('content')] = token.getAttribute('content');
    }
    return headers;
  }

  /**
   * Sends a same-origin JSON request. Resolves with {ok, status, body}; rejects only on network failure,
   * so callers can show a specific message for each outcome.
   */
  function requestJson(url, options) {
    options = options || {};
    var headers = Object.assign({ 'Accept': 'application/json' }, options.headers || {});
    var init = { method: options.method || 'GET', headers: headers, credentials: 'same-origin' };
    if (options.body !== undefined) {
      headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(options.body);
    }
    if (init.method !== 'GET') {
      Object.assign(headers, csrfHeaders());
    }
    return fetch(url, init).then(function (response) {
      var type = response.headers.get('Content-Type') || '';
      var parse = type.indexOf('application/json') >= 0 ? response.json() : Promise.resolve(null);
      return parse.then(function (body) {
        return { ok: response.ok, status: response.status, body: body };
      });
    });
  }

  function escapeHtml(value) {
    return String(value === null || value === undefined ? '' : value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  /* On small screens the sidebar is a drawer: the menu button opens it; the backdrop and Escape close it. */
  function initNavToggle() {
    var button = document.querySelector('[data-ut-toggle="sidenav"]');
    var nav = document.getElementById('ut-sidenav');
    var backdrop = document.querySelector('[data-ut-backdrop="sidenav"]');
    if (!button || !nav) {
      return;
    }
    function setOpen(open) {
      nav.classList.toggle('show', open);
      if (backdrop) {
        backdrop.classList.toggle('show', open);
      }
      button.setAttribute('aria-expanded', open ? 'true' : 'false');
      button.setAttribute('aria-label', open ? 'Close navigation' : 'Open navigation');
    }
    button.addEventListener('click', function () {
      setOpen(!nav.classList.contains('show'));
    });
    if (backdrop) {
      backdrop.addEventListener('click', function () {
        setOpen(false);
      });
    }
    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && nav.classList.contains('show')) {
        setOpen(false);
        button.focus();
      }
    });
  }

  /* Forms with data-ut-confirm ask for confirmation before a state-changing POST. */
  function initConfirmations() {
    document.querySelectorAll('form[data-ut-confirm]').forEach(function (form) {
      form.addEventListener('submit', function (event) {
        if (!window.confirm(form.getAttribute('data-ut-confirm'))) {
          event.preventDefault();
        }
      });
    });
  }

  /* Page-size selectors submit their filter form immediately. */
  function initAutoSubmit() {
    document.querySelectorAll('[data-ut-autosubmit]').forEach(function (input) {
      input.addEventListener('change', function () {
        if (input.form) {
          input.form.submit();
        }
      });
    });
  }

  /* Show/hide password buttons on the sign-in pages. */
  function initPasswordToggles() {
    document.querySelectorAll('[data-ut-toggle-password]').forEach(function (button) {
      var input = document.getElementById(button.getAttribute('data-ut-toggle-password'));
      if (!input) {
        return;
      }
      button.addEventListener('click', function () {
        var show = input.type === 'password';
        input.type = show ? 'text' : 'password';
        button.textContent = show ? 'Hide' : 'Show';
        button.setAttribute('aria-pressed', show ? 'true' : 'false');
      });
    });
  }

  /* Print buttons (inline handlers are not allowed by the content security policy). */
  function initPrintButtons() {
    document.querySelectorAll('[data-ut-print]').forEach(function (button) {
      button.addEventListener('click', function () { window.print(); });
    });
  }

  /* File inputs with a size limit: refuse an oversized file before the upload starts. */
  function initFileLimits() {
    document.querySelectorAll('input[type="file"][data-ut-max-bytes]').forEach(function (input) {
      input.addEventListener('change', function () {
        var max = Number(input.getAttribute('data-ut-max-bytes'));
        var file = input.files && input.files[0];
        var message = file && file.size > max
          ? 'The file is larger than ' + Math.floor(max / 1048576) + ' MB. Choose a smaller file.' : '';
        input.setCustomValidity(message);
        if (message) {
          input.reportValidity();
        }
      });
    });
  }

  /* Demo sign-in: account buttons fill in the username and password. */
  function initDemoAccounts() {
    document.querySelectorAll('[data-ut-demo-username]').forEach(function (button) {
      button.addEventListener('click', function () {
        var username = document.getElementById('username');
        var password = document.getElementById('password');
        if (username && password) {
          username.value = button.getAttribute('data-ut-demo-username');
          password.value = button.getAttribute('data-ut-demo-password');
          password.form.querySelector('button[type="submit"]').focus();
        }
      });
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    initNavToggle();
    initDemoAccounts();
    initPrintButtons();
    initFileLimits();
    initPasswordToggles();
    initConfirmations();
    initAutoSubmit();
  });

  window.Uptrail = { requestJson: requestJson, escapeHtml: escapeHtml };
})();
