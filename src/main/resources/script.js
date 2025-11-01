// ===============================
// 🧠 ЧИСТЫЕ ФУНКЦИИ (NO SIDE EFFECTS)
// ===============================

// Получение элемента по id (вспомогательная, чистая в смысле декларации)
const el = (id) => document.getElementById(id);

// Управление видимостью (возвращает новый стиль, не мутирует DOM)
const computeVisibility = (visible, type = "block") =>
  visible ? type : "none";

// Решаем, нужно ли показывать параметры для эффекта
const shouldShowParams = (effect) => effect === "glitch";

// Формируем данные формы для загрузки
const createFormData = (file, data) => {
  const formData = new FormData();
  Object.entries(data).forEach(([k, v]) => formData.append(k, v));
  formData.append("file", file);
  return formData;
};

// Конвертация Blob → base64
const blobToBase64 = (blob) =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => resolve(reader.result);
    reader.onerror = reject;
    reader.readAsDataURL(blob);
  });

// Сохранение и загрузка из sessionStorage
const saveSession = (key, value) => sessionStorage.setItem(key, value);
const loadSession = (key) => sessionStorage.getItem(key);

// Создание ссылки для скачивания (возвращает элемент, не кликает!)
const createDownloadLink = (dataUrl, filename = "image.png") => {
  const link = document.createElement("a");
  link.href = dataUrl;
  link.download = filename;
  return link;
};

// ===============================
// ⚙️ ЭФФЕКТЫ (IO ACTIONS)
// ===============================

// Управление параметрами глитча
const renderParamsVisibility = (effect) => {
  const params = el("glitchParams");
  params.style.display = computeVisibility(shouldShowParams(effect), "flex");
};

// Отображение изображения
const renderImage = (dataUrl) => {
  const img = el("output");
  const btn = el("downloadBtn");
  img.src = dataUrl;
  img.style.display = "block";
  btn.style.display = "inline-block";
};

// Выполнение загрузки и обработки изображения
const uploadImage = async () => {
  const file = el("fileInput").files[0];
  if (!file) return alert("Выбери изображение!");

  const effect = el("effect").value;
  const glitchShift = getClampedInputValue("glitchShift", 1, 100);
  const glitchIntensity = getClampedInputValue("glitchIntensity", 1, 100);

  const formData = createFormData(file, { effect, glitchShift, glitchIntensity });

  try {
    const response = await fetch("/process", { method: "POST", body: formData });

    // 1️⃣ Ошибка от сервера (HTTP)
    if (!response.ok) {
      console.error(`Server error: ${response.status} ${response.statusText}`);
      alert("Ошибка при обработке изображения! Сервер не смог выполнить запрос.");
      return;
    }

    // 2️⃣ Корректный ответ — читаем blob
    const blob = await response.blob();
    const imgURL = URL.createObjectURL(blob);
    renderImage(imgURL);

    // 💾 Безопасно сохраняем только ссылку (чтобы не было QuotaExceededError)
    sessionStorage.setItem("processedImageURL", imgURL);
    console.log("✅ Image processed and saved as blob URL");

  } catch (err) {
    // 3️⃣ Ошибка уровня сети или клиента (fetch, file, JS)
    console.error("Client/network error:", err);
    console.warn("⚠️ Ошибка сети или проблемы с подключением, но не с сервером.");
    // Здесь alert не обязателен — можно логировать в консоль
  }
};


// Скачивание изображения
const downloadImage = () => {
  const saved = loadSession("processedImage");
  if (!saved) return;
  const link = createDownloadLink(saved, "transformed_image.png");
  link.click();
};

// Чистая функция для ограничения числа
const clamp = (value, min, max) => Math.min(max, Math.max(min, value));

// Получение значения input с ограничением
const getClampedInputValue = (id, min, max) => {
  const elInput = el(id);
  if (!elInput) return min;
  const val = parseInt(elInput.value, 10);
  const clamped = clamp(isNaN(val) ? min : val, min, max);
  elInput.value = clamped; // обновляем поле, чтобы отразить ограничение
  return clamped;
};

// ===============================
// 🚀 ИНИЦИАЛИЗАЦИЯ (MAIN ENTRY)
// ===============================

const init = () => {
  const saved = loadSession("processedImage");
  if (saved) renderImage(saved);

  el("effect").addEventListener("change", (e) => renderParamsVisibility(e.target.value));
  el("processBtn").addEventListener("click", uploadImage);
  el("downloadBtn").addEventListener("click", downloadImage);
};

window.addEventListener("DOMContentLoaded", init);
