/*
 * Training calendar: loads a month from GET /api/v1/calendar and renders a month grid or a list.
 * Previous/next, the month drop-down and the category filter reload data without leaving the page.
 */
(function () {
  'use strict';

  var root = document.getElementById('calendar');
  if (!root || !window.Uptrail) {
    return;
  }
  var U = window.Uptrail;
  var monthSelect = document.getElementById('calendar-month');
  var categorySelect = document.getElementById('calendar-category');
  var title = document.getElementById('calendar-title');
  var countLabel = document.getElementById('calendar-count');
  var state = {
    month: root.getAttribute('data-month'),
    category: root.getAttribute('data-category') || '',
    view: 'grid',
    data: null
  };
  var today = root.getAttribute('data-today');
  var MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September',
    'October', 'November', 'December'];
  var DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

  function pad(n) { return n < 10 ? '0' + n : String(n); }
  function iso(date) { return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate()); }
  function parse(value) {
    var p = value.split('-');
    return new Date(Number(p[0]), Number(p[1]) - 1, Number(p[2] || 1));
  }
  function label(month) {
    var d = parse(month + '-01');
    return MONTHS[d.getMonth()] + ' ' + d.getFullYear();
  }
  function shift(month, delta) {
    var d = parse(month + '-01');
    d.setMonth(d.getMonth() + delta);
    return d.getFullYear() + '-' + pad(d.getMonth() + 1);
  }
  function shortDate(value) {
    var d = parse(value);
    return d.getDate() + ' ' + MONTHS[d.getMonth()].substring(0, 3) + ' ' + d.getFullYear();
  }
  function period(entry) {
    if (entry.startDate === entry.endDate) {
      var day = shortDate(entry.startDate);
      if (entry.startSession === 'AM' && entry.endSession === 'PM') {
        return day;
      }
      return day + (entry.startSession === 'AM' ? ' (morning)' : ' (afternoon)');
    }
    return shortDate(entry.startDate) + (entry.startSession === 'PM' ? ' (afternoon)' : '') + ' – ' +
      shortDate(entry.endDate) + (entry.endSession === 'AM' ? ' (morning)' : '');
  }

  function renderGrid(data) {
    var first = parse(data.month + '-01');
    var holidays = {};
    data.holidays.forEach(function (h) { holidays[h.date] = h.name; });
    var start = new Date(first);
    start.setDate(1 - ((first.getDay() + 6) % 7));
    var html = '<div class="ut-table-wrap"><table class="ut-calendar"><thead><tr>' +
      DAYS.map(function (d) { return '<th scope="col">' + d + '</th>'; }).join('') + '</tr></thead><tbody>';
    var day = new Date(start);
    for (var week = 0; week < 6; week++) {
      if (week > 0 && day.getMonth() !== first.getMonth()) {
        break;
      }
      html += '<tr>';
      for (var i = 0; i < 7; i++) {
        var key = iso(day);
        var weekend = i >= 5;
        var classes = [];
        if (day.getMonth() !== first.getMonth()) { classes.push('ut-other-month'); }
        if (weekend) { classes.push('ut-weekend'); }
        if (key === today) { classes.push('ut-today'); }
        html += '<td class="' + classes.join(' ') + '"><div class="ut-day-number">' + day.getDate() + '</div>';
        if (holidays[key]) {
          html += '<div class="ut-day-holiday">' + U.escapeHtml(holidays[key]) + '</div>';
        }
        if (!weekend && !holidays[key]) {
          var todays = data.entries.filter(function (e) { return e.startDate <= key && e.endDate >= key; });
          todays.slice(0, 4).forEach(function (e) {
            var text = e.employeeName + ': ' + e.courseTitle;
            html += '<span class="ut-cal-event cat-' + e.category.toLowerCase() + '" title="' +
              U.escapeHtml(text + ' (' + e.categoryName + ', ' + period(e) + ')') + '">' + U.escapeHtml(text) + '</span>';
          });
          if (todays.length > 4) {
            html += '<span class="small-ut text-muted-ut">+' + (todays.length - 4) + ' more (see List)</span>';
          }
        }
        html += '</td>';
        day.setDate(day.getDate() + 1);
      }
      html += '</tr>';
    }
    return html + '</tbody></table></div>';
  }

  function renderList(data) {
    if (!data.entries.length) {
      return '<p class="text-muted-ut mb-0">No approved courses this month.</p>';
    }
    return '<div class="ut-table-wrap"><table class="ut-table"><thead><tr><th>Staff member</th><th>Course</th>' +
      '<th>Category</th><th>Dates</th></tr></thead><tbody>' +
      data.entries.map(function (e) {
        return '<tr><td>' + U.escapeHtml(e.employeeName) + '</td><td>' + U.escapeHtml(e.courseTitle) + '</td><td>' +
          '<span class="ut-cal-event cat-' + e.category.toLowerCase() + '">' + U.escapeHtml(e.categoryName) +
          '</span></td><td>' + U.escapeHtml(period(e)) + '</td></tr>';
      }).join('') + '</tbody></table></div>';
  }

  function render() {
    if (!state.data) {
      return;
    }
    title.textContent = label(state.data.month);
    countLabel.textContent = state.data.entries.length + (state.data.entries.length === 1 ? ' course' : ' courses');
    root.innerHTML = state.view === 'grid' ? renderGrid(state.data) : renderList(state.data);
    document.querySelectorAll('[data-calendar-view]').forEach(function (button) {
      button.setAttribute('aria-pressed', button.getAttribute('data-calendar-view') === state.view ? 'true' : 'false');
      button.classList.toggle('btn-primary', button.getAttribute('data-calendar-view') === state.view);
      button.classList.toggle('btn-secondary', button.getAttribute('data-calendar-view') !== state.view);
    });
  }

  function syncControls() {
    if (monthSelect) {
      var exists = Array.prototype.some.call(monthSelect.options, function (o) { return o.value === state.month; });
      if (!exists) {
        var option = document.createElement('option');
        option.value = state.month;
        option.textContent = label(state.month);
        monthSelect.appendChild(option);
      }
      monthSelect.value = state.month;
    }
    var query = '?month=' + encodeURIComponent(state.month) +
      (state.category ? '&category=' + encodeURIComponent(state.category) : '');
    var categoryQuery = state.category ? '&category=' + encodeURIComponent(state.category) : '';
    document.getElementById('calendar-prev').href = '?month=' + shift(state.month, -1) + categoryQuery;
    document.getElementById('calendar-next').href = '?month=' + shift(state.month, 1) + categoryQuery;
    window.history.replaceState(null, '', query);
  }

  function load() {
    root.setAttribute('aria-busy', 'true');
    var url = root.getAttribute('data-api') + '?month=' + encodeURIComponent(state.month) +
      (state.category ? '&category=' + encodeURIComponent(state.category) : '');
    U.requestJson(url).then(function (response) {
      if (response.ok && response.body) {
        state.data = response.body;
        syncControls();
        render();
      } else if (response.status === 401) {
        root.innerHTML = '<div class="ut-strip state-critical mb-0">Your session has ended. Sign in again to see the calendar.</div>';
      } else {
        root.innerHTML = '<div class="ut-strip state-negative mb-0">Unable to load the calendar. Reload the page to try again.</div>';
      }
    }).catch(function () {
      root.innerHTML = '<div class="ut-strip state-negative mb-0">Unable to load the calendar. Check your connection and reload the page.</div>';
    }).finally(function () { root.removeAttribute('aria-busy'); });
  }

  document.getElementById('calendar-prev').addEventListener('click', function (event) {
    event.preventDefault();
    state.month = shift(state.month, -1);
    load();
  });
  document.getElementById('calendar-next').addEventListener('click', function (event) {
    event.preventDefault();
    state.month = shift(state.month, 1);
    load();
  });
  if (monthSelect) {
    monthSelect.addEventListener('change', function () { state.month = monthSelect.value; load(); });
  }
  if (categorySelect) {
    categorySelect.addEventListener('change', function () { state.category = categorySelect.value; load(); });
  }
  document.querySelectorAll('[data-calendar-view]').forEach(function (button) {
    button.addEventListener('click', function () {
      state.view = button.getAttribute('data-calendar-view');
      render();
    });
  });
  document.getElementById('calendar-toolbar').addEventListener('submit', function (event) {
    event.preventDefault();
    load();
  });

  load();
})();
