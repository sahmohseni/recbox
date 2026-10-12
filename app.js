const screens = [...document.querySelectorAll('.screen')];
const toast = document.getElementById('toast');
const toastText = document.getElementById('toastText');
let activeMode = 'audio';
let elapsed = 0;
let timerInterval;

const modeLabels = {audio:'ضبط صدای محیط', video:'ضبط ویدیو', photo:'ثبت عکس', call:'ضبط مکالمه'};

function showScreen(id) {
  screens.forEach(screen => screen.classList.toggle('active', screen.id === id));
  document.querySelectorAll('[data-nav]').forEach(item => item.classList.toggle('active', item.dataset.nav === id));
}

function notify(message) {
  toastText.textContent = message;
  toast.classList.add('show');
  window.clearTimeout(notify.timeout);
  notify.timeout = window.setTimeout(() => toast.classList.remove('show'), 2400);
}

function startRecording(mode) {
  activeMode = mode || 'audio';
  document.getElementById('recordingMode').textContent = modeLabels[activeMode];
  elapsed = 0;
  updateRecordingTime();
  window.clearInterval(timerInterval);
  timerInterval = window.setInterval(() => { elapsed += 1; updateRecordingTime(); }, 1000);
  showScreen('screen-recording');
}

function updateRecordingTime() {
  const hours = String(Math.floor(elapsed / 3600)).padStart(2, '0');
  const minutes = String(Math.floor((elapsed % 3600) / 60)).padStart(2, '0');
  const seconds = String(elapsed % 60).padStart(2, '0');
  document.getElementById('recordingTime').textContent = `${hours}:${minutes}:${seconds}`;
}

document.addEventListener('click', event => {
  const go = event.target.closest('[data-go]');
  const back = event.target.closest('[data-back]');
  const nav = event.target.closest('[data-nav]');
  const modeButton = event.target.closest('[data-mode]');
  if (go) showScreen(go.dataset.go);
  if (back) showScreen(back.dataset.back);
  if (nav) showScreen(nav.dataset.nav);
  if (modeButton) startRecording(modeButton.dataset.mode);
});

document.getElementById('stopButton').addEventListener('click', () => {
  window.clearInterval(timerInterval);
  showScreen('screen-home');
  notify('ضبط شما در آرشیو ذخیره شد');
});

document.getElementById('pauseButton').addEventListener('click', event => {
  const paused = event.currentTarget.classList.toggle('paused');
  if (paused) { window.clearInterval(timerInterval); event.currentTarget.querySelector('span').textContent = '▶'; event.currentTarget.querySelector('small').textContent = 'ادامه'; notify('ضبط موقتاً متوقف شد'); }
  else { timerInterval = window.setInterval(() => { elapsed += 1; updateRecordingTime(); }, 1000); event.currentTarget.querySelector('span').textContent = 'Ⅱ'; event.currentTarget.querySelector('small').textContent = 'مکث'; notify('ضبط ادامه پیدا کرد'); }
});
