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

  function initNavToggle() {
    var button = document.querySelector('[data-ut-toggle="sidenav"]');
    var nav = document.getElementById('ut-sidenav');
    if (!button || !nav) {
      return;
    }
    button.addEventListener('click', function () {
      var open = nav.classList.toggle('show');
      button.setAttribute('aria-expanded', open ? 'true' : 'false');
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

  document.addEventListener('DOMContentLoaded', function () {
    initNavToggle();
    initPrintButtons();
    initPasswordToggles();
    initConfirmations();
    initAutoSubmit();
  });

  window.Uptrail = { requestJson: requestJson, escapeHtml: escapeHtml };
})();
