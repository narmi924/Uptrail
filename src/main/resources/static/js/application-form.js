/*
 * Application form behaviour: live eligibility check (POST /api/v1/applications/preview), catalogue search
 * (GET /api/v1/catalogue) and category-dependent fields. The server validates everything again on submit.
 */
(function () {
  'use strict';

  var form = document.getElementById('application-form');
  var panel = document.getElementById('eligibility');
  if (!form || !panel || !window.Uptrail) {
    return;
  }
  var U = window.Uptrail;
  var field = function (name) { return form.elements.namedItem(name); };
  var timer = null;
  var sequence = 0;

  function value(name) {
    var element = field(name);
    return element && element.value !== '' ? element.value : null;
  }

  function categoryAllowsHalfDay() {
    return value('category') === 'INTERNAL';
  }

  function syncCategoryFields() {
    var halfDay = categoryAllowsHalfDay();
    document.querySelectorAll('[data-half-day-field]').forEach(function (element) {
      element.hidden = !halfDay;
    });
    if (!halfDay) {
      field('startSession').value = 'AM';
      field('endSession').value = 'PM';
    }
    var fee = field('courseFee');
    if (value('category') === 'INTERNAL') {
      fee.value = '0.00';
      fee.readOnly = true;
    } else {
      fee.readOnly = false;
    }
  }

  function requestBody() {
    var applicationId = panel.getAttribute('data-application-id');
    return {
      applicationId: applicationId ? Number(applicationId) : null,
      category: value('category'),
      catalogueId: value('catalogueId') ? Number(value('catalogueId')) : null,
      courseTitle: value('courseTitle'),
      providerName: value('providerName'),
      startDate: value('startDate'),
      endDate: value('endDate'),
      startSession: value('startSession'),
      endSession: value('endSession'),
      courseFee: value('courseFee'),
      justification: value('justification'),
      workDissemination: value('workDissemination')
    };
  }

  function row(label, content, extraClass) {
    return '<div class="ut-check-row"><span class="ut-check-label">' + U.escapeHtml(label) + '</span>' +
      '<span class="ut-check-value ' + (extraClass || '') + '">' + content + '</span></div>';
  }

  function money(amount) {
    var number = Number(amount);
    return 'SGD ' + number.toLocaleString('en-SG', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function formatDate(iso) {
    var parts = iso.split('-');
    var date = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
    return date.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
  }

  function render(result) {
    var html = '';
    if (result.eligible) {
      html += '<div class="ut-strip state-positive mb-3">Eligible to submit. The server checks again on submission.</div>';
    } else if (result.problems.length) {
      html += '<div class="ut-strip state-negative mb-3"><div class="ut-strip-title">Not eligible yet</div><ul>' +
        result.problems.map(function (p) { return '<li>' + U.escapeHtml(p.message) + '</li>'; }).join('') +
        '</ul></div>';
    }
    if (result.trainingUnits > 0) {
      html += row('Training days', U.escapeHtml(result.trainingDays));
    }
    result.annualAllocations.forEach(function (year) {
      html += '<div class="ut-section-title">' + year.year + '</div>';
      if (!year.configured) {
        html += row('Entitlement', 'Not configured', 'ut-value-negative');
        return;
      }
      html += row('Days requested', U.escapeHtml(year.daysRequested));
      html += row('Days available now', U.escapeHtml(year.daysAvailableBefore));
      html += row('Days after this application', U.escapeHtml(year.daysAvailableAfter),
        year.unitsAvailableAfter < 0 ? 'ut-value-negative' : '');
      if (Number(year.feeRequested) > 0) {
        html += row('Fee charged to ' + year.year, money(year.feeRequested));
        html += row('Budget available now', money(year.budgetAvailableBefore));
        html += row('Budget after this application', money(year.budgetAvailableAfter),
          Number(year.budgetAvailableAfter) < 0 ? 'ut-value-negative' : '');
      }
    });
    if (result.includedDates.length) {
      html += '<div class="ut-section-title">Counted days</div><ul class="small-ut ps-3 mb-2">' +
        result.includedDates.map(function (d) {
          var part = d.session === 'BOTH' ? 'full day' : (d.session === 'AM' ? 'morning' : 'afternoon');
          return '<li>' + U.escapeHtml(formatDate(d.date)) + ' – ' + part + '</li>';
        }).join('') + '</ul>';
    }
    if (result.excludedDates.length) {
      html += '<div class="ut-section-title">Not counted</div><ul class="small-ut ps-3 mb-2">' +
        result.excludedDates.map(function (d) {
          return '<li>' + U.escapeHtml(formatDate(d.date)) + ' – ' + U.escapeHtml(d.reason) + '</li>';
        }).join('') + '</ul>';
    }
    if (result.conflicts.length) {
      html += '<div class="ut-section-title">Overlapping applications</div><ul class="small-ut ps-3 mb-0">' +
        result.conflicts.map(function (c) {
          return '<li>' + U.escapeHtml(c.referenceNo) + ' ' + U.escapeHtml(c.courseTitle) + ' (' +
            U.escapeHtml(c.status) + ')</li>';
        }).join('') + '</ul>';
    }
    panel.innerHTML = html || '<p class="text-muted-ut mb-0">Enter the category and dates to see the result.</p>';
  }

  function renderUnavailable(message) {
    panel.innerHTML = '<div class="ut-strip state-critical mb-0">' + U.escapeHtml(message) + '</div>';
  }

  function check() {
    if (!value('category') || !value('startDate') || !value('endDate')) {
      return;
    }
    var mine = ++sequence;
    panel.setAttribute('aria-busy', 'true');
    U.requestJson(panel.getAttribute('data-preview-url'), { method: 'POST', body: requestBody() })
      .then(function (response) {
        if (mine !== sequence) {
          return;
        }
        if (response.ok && response.body) {
          render(response.body);
        } else if (response.status === 401) {
          renderUnavailable('Your session has ended. Sign in again before submitting.');
        } else if (response.body && response.body.message) {
          renderUnavailable(response.body.message);
        } else {
          renderUnavailable('Unable to check now. You can still submit; the server validates again.');
        }
      })
      .catch(function () {
        if (mine === sequence) {
          renderUnavailable('Unable to check now. You can still submit; the server validates again.');
        }
      })
      .finally(function () { panel.removeAttribute('aria-busy'); });
  }

  function scheduleCheck() {
    window.clearTimeout(timer);
    timer = window.setTimeout(check, 350);
  }

  /* Catalogue search */
  var search = document.getElementById('catalogue-search');
  var results = document.getElementById('catalogue-results');
  var searchTimer = null;

  function renderResults(items) {
    results.innerHTML = items.map(function (item, index) {
      var fee = Number(item.defaultFee) > 0 ? money(item.defaultFee) : 'No fee';
      return '<button type="button" class="list-group-item list-group-item-action" data-index="' + index + '">' +
        '<strong>' + U.escapeHtml(item.title) + '</strong><br><span class="small-ut text-muted-ut">' +
        U.escapeHtml(item.category) + ' · ' + U.escapeHtml(item.providerName || 'In-house') + ' · ' + fee +
        '</span></button>';
    }).join('') || '<div class="list-group-item small-ut text-muted-ut">No catalogue course matches. You can still enter the details yourself.</div>';
    results.querySelectorAll('button[data-index]').forEach(function (button) {
      button.addEventListener('click', function () {
        var item = items[Number(button.getAttribute('data-index'))];
        field('catalogueId').value = item.id;
        field('category').value = item.category;
        field('courseTitle').value = item.title;
        field('providerName').value = item.providerName || '';
        field('courseFee').value = Number(item.defaultFee).toFixed(2);
        results.innerHTML = '';
        search.value = '';
        syncCategoryFields();
        scheduleCheck();
      });
    });
  }

  if (search && results) {
    search.addEventListener('input', function () {
      window.clearTimeout(searchTimer);
      var query = search.value.trim();
      if (query.length < 2) {
        results.innerHTML = '';
        return;
      }
      searchTimer = window.setTimeout(function () {
        U.requestJson(search.getAttribute('data-api') + '?q=' + encodeURIComponent(query))
          .then(function (response) {
            if (response.ok && Array.isArray(response.body)) {
              renderResults(response.body);
            } else {
              results.innerHTML = '<div class="list-group-item small-ut text-muted-ut">The catalogue is unavailable right now.</div>';
            }
          })
          .catch(function () {
            results.innerHTML = '<div class="list-group-item small-ut text-muted-ut">The catalogue is unavailable right now.</div>';
          });
      }, 250);
    });
  }

  /* A manual change of the course details detaches the form from the catalogue template. */
  ['courseTitle', 'category'].forEach(function (name) {
    field(name).addEventListener('input', function () { field('catalogueId').value = ''; });
  });

  field('category').addEventListener('change', syncCategoryFields);
  ['category', 'startDate', 'endDate', 'startSession', 'endSession', 'courseFee'].forEach(function (name) {
    field(name).addEventListener('change', scheduleCheck);
    field(name).addEventListener('input', scheduleCheck);
  });

  syncCategoryFields();
  check();
})();
