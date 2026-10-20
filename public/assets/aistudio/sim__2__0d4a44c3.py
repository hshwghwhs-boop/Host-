# -*- coding: utf-8 -*-
import telebot
import subprocess
import os
import zipfile
import tempfile
import shutil
from telebot import types
import time
from datetime import datetime, timedelta
import psutil
import sqlite3
import json
import logging
import signal
import threading
import re
import sys
import atexit
import requests

# --- Flask Keep Alive ---
from flask import Flask
from threading import Thread

app = Flask('')

@app.route('/')
def home():
    return "I'am Marco File Host"

def run_flask():
  port = int(os.environ.get("PORT", 8080))
  app.run(host='0.0.0.0', port=port)

def keep_alive():
    t = Thread(target=run_flask)
    t.daemon = True
    t.start()
    print("Flask Keep-Alive server started.")
# --- End Flask Keep Alive ---

# --- Configuration ---
TOKEN = '8893974995:AAGnwSeD3jarEHUjwpE6_wWLGzHZ4G7JGlM'
OWNER_ID = 8714511542
ADMIN_ID = 8714511542
YOUR_USERNAME = '@ll_DARK_GETO_ll'
UPDATE_CHANNEL = '@geto_bot_hub'

ADMIN_APPROVAL_REQUIRED = True

# Folder setup
BASE_DIR = os.path.abspath(os.path.dirname(__file__))
UPLOAD_BOTS_DIR = os.path.join(BASE_DIR, 'upload_bots')
IROTECH_DIR = os.path.join(BASE_DIR, 'inf')
DATABASE_PATH = os.path.join(IROTECH_DIR, 'bot_data.db')

# File upload limits
FREE_USER_LIMIT = 3
SUBSCRIBED_USER_LIMIT = 15
ADMIN_LIMIT = 999
OWNER_LIMIT = float('inf')

os.makedirs(UPLOAD_BOTS_DIR, exist_ok=True)
os.makedirs(IROTECH_DIR, exist_ok=True)

bot = telebot.TeleBot(TOKEN)

# --- Data structures ---
bot_scripts = {}
user_subscriptions = {}
user_files = {}
active_users = set()
admin_ids = {ADMIN_ID, OWNER_ID}
bot_locked = False
pending_approvals = {}

# --- Logging Setup ---
logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# --- Command Button Layouts (STYLISH FONT) ---
COMMAND_BUTTONS_LAYOUT_USER_SPEC = [
    ["📢 𝐔ᴩᴅᴀᴛᴇꜱ 𝐂ʜᴀɴɴᴇʟ"],
    ["📤 𝐔ᴩʟᴏᴀᴅ 𝐅ɪʟᴇ", "📂 𝐂ʜᴇᴄᴋ 𝐅ɪʟᴇꜱ"],
    ["⚡ 𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ", "📊 𝐒ᴛᴀᴛɪꜱᴛɪᴄꜱ"],
    ["📞 𝐂ᴏɴᴛᴀᴄᴛ 𝐎ᴡɴᴇʀ"]
]
ADMIN_COMMAND_BUTTONS_LAYOUT_USER_SPEC = [
    ["📢 𝐔ᴩᴅᴀᴛᴇꜱ 𝐂ʜᴀɴɴᴇʟ"],
    ["📤 𝐔ᴩʟᴏᴀᴅ 𝐅ɪʟᴇ", "📂 𝐂ʜᴇᴄᴋ 𝐅ɪʟᴇꜱ"],
    ["⚡ 𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ", "📊 𝐒ᴛᴀᴛɪꜱᴛɪᴄꜱ"],
    ["💳 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴꜱ", "📢 𝐁ʀᴏᴀᴅᴄᴀꜱᴛ"],
    ["🔒 𝐋ᴏᴄᴋ 𝐁ᴏᴛ", "🟢 𝐑ᴜɴɴɪɴɢ 𝐀ʟʟ 𝐂ᴏᴅᴇ"],
    ["⏳ 𝐏ᴇɴᴅɪɴɢ 𝐅ɪʟᴇꜱ", "👑 𝐀ᴅᴍɪɴ 𝐏ᴀɴᴇʟ"],
    ["📞 𝐂ᴏɴᴛᴀᴄᴛ 𝐎ᴡɴᴇʀ"]
]

# --- Database Setup ---
def init_db():
    logger.info(f"Initializing database at: {DATABASE_PATH}")
    try:
        conn = sqlite3.connect(DATABASE_PATH, check_same_thread=False)
        c = conn.cursor()
        c.execute('''CREATE TABLE IF NOT EXISTS subscriptions
                     (user_id INTEGER PRIMARY KEY, expiry TEXT)''')
        c.execute('''CREATE TABLE IF NOT EXISTS user_files
                     (user_id INTEGER, file_name TEXT, file_type TEXT,
                      PRIMARY KEY (user_id, file_name))''')
        c.execute('''CREATE TABLE IF NOT EXISTS active_users
                     (user_id INTEGER PRIMARY KEY)''')
        c.execute('''CREATE TABLE IF NOT EXISTS admins
                     (user_id INTEGER PRIMARY KEY)''')
        c.execute('''CREATE TABLE IF NOT EXISTS pending_files
                     (user_id INTEGER, file_name TEXT, file_type TEXT,
                      upload_time TEXT, message_id INTEGER,
                      PRIMARY KEY (user_id, file_name))''')
        c.execute('INSERT OR IGNORE INTO admins (user_id) VALUES (?)', (OWNER_ID,))
        if ADMIN_ID != OWNER_ID:
             c.execute('INSERT OR IGNORE INTO admins (user_id) VALUES (?)', (ADMIN_ID,))
        conn.commit()
        conn.close()
        logger.info("Database initialized successfully.")
    except Exception as e:
        logger.error(f"❌ Database initialization error: {e}", exc_info=True)

def load_data():
    logger.info("Loading data from database...")
    try:
        conn = sqlite3.connect(DATABASE_PATH, check_same_thread=False)
        c = conn.cursor()
        c.execute('SELECT user_id, expiry FROM subscriptions')
        for user_id, expiry in c.fetchall():
            try:
                user_subscriptions[user_id] = {'expiry': datetime.fromisoformat(expiry)}
            except ValueError:
                logger.warning(f"⚠️ Invalid expiry for {user_id}: {expiry}")
        c.execute('SELECT user_id, file_name, file_type FROM user_files')
        for user_id, file_name, file_type in c.fetchall():
            if user_id not in user_files:
                user_files[user_id] = []
            user_files[user_id].append((file_name, file_type))
        c.execute('SELECT user_id FROM active_users')
        active_users.update(user_id for (user_id,) in c.fetchall())
        c.execute('SELECT user_id FROM admins')
        admin_ids.update(user_id for (user_id,) in c.fetchall())
        conn.close()
        logger.info(f"Data loaded: {len(active_users)} users, {len(user_subscriptions)} subs, {len(admin_ids)} admins.")
    except Exception as e:
        logger.error(f"❌ Error loading data: {e}", exc_info=True)

def load_pending_files():
    logger.info("Loading pending files from database...")
    try:
        conn = sqlite3.connect(DATABASE_PATH, check_same_thread=False)
        c = conn.cursor()
        c.execute('SELECT user_id, file_name, file_type, message_id FROM pending_files')
        for user_id, file_name, file_type, msg_id in c.fetchall():
            if user_id not in pending_approvals:
                pending_approvals[user_id] = []
            pending_approvals[user_id].append((file_name, file_type, msg_id, datetime.now()))
        conn.close()
        total = sum(len(v) for v in pending_approvals.values())
        logger.info(f"Loaded {total} pending files.")
    except Exception as e:
        logger.error(f"Error loading pending files: {e}", exc_info=True)

init_db()
load_data()
load_pending_files()
# --- End Database Setup ---

# --- Helper Functions ---
def get_user_folder(user_id):
    user_folder = os.path.join(UPLOAD_BOTS_DIR, str(user_id))
    os.makedirs(user_folder, exist_ok=True)
    return user_folder

def get_user_file_limit(user_id):
    if user_id == OWNER_ID: return OWNER_LIMIT
    if user_id in admin_ids: return ADMIN_LIMIT
    if user_id in user_subscriptions and user_subscriptions[user_id]['expiry'] > datetime.now():
        return SUBSCRIBED_USER_LIMIT
    return FREE_USER_LIMIT

def get_user_file_count(user_id):
    return len(user_files.get(user_id, []))

def is_bot_running(script_owner_id, file_name):
    script_key = f"{script_owner_id}_{file_name}"
    script_info = bot_scripts.get(script_key)
    if script_info and script_info.get('process'):
        try:
            proc = psutil.Process(script_info['process'].pid)
            is_running = proc.is_running() and proc.status() != psutil.STATUS_ZOMBIE
            if not is_running:
                if 'log_file' in script_info and hasattr(script_info['log_file'], 'close') and not script_info['log_file'].closed:
                    try: script_info['log_file'].close()
                    except Exception as log_e: logger.error(f"Error closing log file: {log_e}")
                if script_key in bot_scripts:
                    del bot_scripts[script_key]
            return is_running
        except psutil.NoSuchProcess:
            if 'log_file' in script_info and hasattr(script_info['log_file'], 'close') and not script_info['log_file'].closed:
                try: script_info['log_file'].close()
                except Exception as log_e: logger.error(f"Error closing log file: {log_e}")
            if script_key in bot_scripts:
                 del bot_scripts[script_key]
            return False
        except Exception as e:
            logger.error(f"Error checking process status for {script_key}: {e}", exc_info=True)
            return False
    return False


def kill_process_tree(process_info):
    pid = None
    log_file_closed = False
    script_key = process_info.get('script_key', 'N/A')

    try:
        if 'log_file' in process_info and hasattr(process_info['log_file'], 'close') and not process_info['log_file'].closed:
            try:
                process_info['log_file'].close()
                log_file_closed = True
                logger.info(f"Closed log file for {script_key}")
            except Exception as log_e:
                logger.error(f"Error closing log file for {script_key}: {log_e}")

        process = process_info.get('process')
        if process and hasattr(process, 'pid'):
           pid = process.pid
           if pid:
                try:
                    parent = psutil.Process(pid)
                    children = parent.children(recursive=True)
                    logger.info(f"Killing process tree for {script_key} (PID: {pid})")

                    for child in children:
                        try: child.terminate()
                        except psutil.NoSuchProcess: pass
                        except Exception as e:
                            try: child.kill()
                            except Exception as e2: logger.error(f"Failed to kill child {child.pid}: {e2}")

                    gone, alive = psutil.wait_procs(children, timeout=1)
                    for p in alive:
                        try: p.kill()
                        except Exception as e: logger.error(f"Failed to kill child {p.pid}: {e}")

                    try:
                        parent.terminate()
                        try: parent.wait(timeout=1)
                        except psutil.TimeoutExpired:
                            parent.kill()
                    except psutil.NoSuchProcess:
                        pass
                    except Exception as e:
                        try: parent.kill()
                        except Exception as e2: logger.error(f"Failed to kill parent {pid}: {e2}")

                except psutil.NoSuchProcess:
                    logger.warning(f"Process {pid} not found.")
           else: logger.error(f"Process PID is None for {script_key}.")
    except Exception as e:
        logger.error(f"❌ Unexpected error killing process tree: {e}", exc_info=True)

# --- Automatic Package Installation ---
def attempt_install_pip(module_name, message):
    package_name = TELEGRAM_MODULES.get(module_name.lower(), module_name)
    if package_name is None:
        return False
    try:
        bot.reply_to(message, f"🐍 𝐈ɴꜱᴛᴀʟʟɪɴɢ `{package_name}`...", parse_mode='Markdown')
        command = [sys.executable, '-m', 'pip', 'install', package_name]
        result = subprocess.run(command, capture_output=True, text=True, check=False, encoding='utf-8', errors='ignore')
        if result.returncode == 0:
            bot.reply_to(message, f"✅ 𝐏ᴀᴄᴋᴀɢᴇ `{package_name}` 𝐈ɴꜱᴛᴀʟʟᴇᴅ.", parse_mode='Markdown')
            return True
        else:
            error_msg = f"❌ 𝐅ᴀɪʟᴇᴅ ᴛᴏ 𝐈ɴꜱᴛᴀʟʟ `{package_name}`.\n```\n{result.stderr or result.stdout}\n```"
            if len(error_msg) > 4000: error_msg = error_msg[:4000] + "\n... (truncated)"
            bot.reply_to(message, error_msg, parse_mode='Markdown')
            return False
    except Exception as e:
        bot.reply_to(message, f"❌ 𝐄ʀʀᴏʀ: {str(e)}")
        return False

def attempt_install_npm(module_name, user_folder, message):
    try:
        bot.reply_to(message, f"🟠 𝐈ɴꜱᴛᴀʟʟɪɴɢ `{module_name}`...", parse_mode='Markdown')
        command = ['npm', 'install', module_name]
        result = subprocess.run(command, capture_output=True, text=True, check=False, cwd=user_folder, encoding='utf-8', errors='ignore')
        if result.returncode == 0:
            bot.reply_to(message, f"✅ 𝐍ᴏᴅᴇ 𝐏ᴀᴄᴋᴀɢᴇ `{module_name}` 𝐈ɴꜱᴛᴀʟʟᴇᴅ.", parse_mode='Markdown')
            return True
        else:
            error_msg = f"❌ 𝐅ᴀɪʟᴇᴅ ᴛᴏ 𝐈ɴꜱᴛᴀʟʟ `{module_name}`.\n```\n{result.stderr or result.stdout}\n```"
            if len(error_msg) > 4000: error_msg = error_msg[:4000] + "\n... (truncated)"
            bot.reply_to(message, error_msg, parse_mode='Markdown')
            return False
    except FileNotFoundError:
         bot.reply_to(message, "❌ 'npm' ɴᴏᴛ ꜰᴏᴜɴᴅ.")
         return False
    except Exception as e:
        bot.reply_to(message, f"❌ 𝐄ʀʀᴏʀ: {str(e)}")
        return False

def run_script(script_path, script_owner_id, user_folder, file_name, message_obj_for_reply, attempt=1):
    max_attempts = 2
    if attempt > max_attempts:
        bot.reply_to(message_obj_for_reply, f"❌ 𝐅ᴀɪʟᴇᴅ ᴀꜰᴛᴇʀ {max_attempts} ᴀᴛᴛᴇᴍᴩᴛꜱ.")
        return

    script_key = f"{script_owner_id}_{file_name}"
    logger.info(f"Attempt {attempt} Python: {script_path}")

    try:
        if not os.path.exists(script_path):
             bot.reply_to(message_obj_for_reply, f"❌ 𝐒ᴄʀɪᴩᴛ ɴᴏᴛ ꜰᴏᴜɴᴅ!")
             if script_owner_id in user_files:
                 user_files[script_owner_id] = [f for f in user_files.get(script_owner_id, []) if f[0] != file_name]
             remove_user_file_db(script_owner_id, file_name)
             return

        if attempt == 1:
            check_command = [sys.executable, script_path]
            check_proc = None
            try:
                check_proc = subprocess.Popen(check_command, cwd=user_folder, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding='utf-8', errors='ignore')
                stdout, stderr = check_proc.communicate(timeout=5)
                return_code = check_proc.returncode
                if return_code != 0 and stderr:
                    match_py = re.search(r"ModuleNotFoundError: No module named '(.+?)'", stderr)
                    if match_py:
                        module_name = match_py.group(1).strip().strip("'\"")
                        if attempt_install_pip(module_name, message_obj_for_reply):
                            time.sleep(2)
                            threading.Thread(target=run_script, args=(script_path, script_owner_id, user_folder, file_name, message_obj_for_reply, attempt + 1)).start()
                            return
                        else:
                            bot.reply_to(message_obj_for_reply, f"❌ 𝐈ɴꜱᴛᴀʟʟ ꜰᴀɪʟᴇᴅ.")
                            return
                    else:
                         error_summary = stderr[:500]
                         bot.reply_to(message_obj_for_reply, f"❌ 𝐄ʀʀᴏʀ:\n```\n{error_summary}\n```", parse_mode='Markdown')
                         return
            except subprocess.TimeoutExpired:
                if check_proc and check_proc.poll() is None: check_proc.kill(); check_proc.communicate()
            except FileNotFoundError:
                 bot.reply_to(message_obj_for_reply, f"❌ 𝐏ʏᴛʜᴏɴ ɪɴᴛᴇʀᴩʀᴇᴛᴇʀ ɴᴏᴛ ꜰᴏᴜɴᴅ.")
                 return
            except Exception as e:
                 bot.reply_to(message_obj_for_reply, f"❌ 𝐄ʀʀᴏʀ: {e}")
                 return
            finally:
                 if check_proc and check_proc.poll() is None:
                     check_proc.kill(); check_proc.communicate()

        log_file_path = os.path.join(user_folder, f"{os.path.splitext(file_name)[0]}.log")
        log_file = None; process = None
        try: log_file = open(log_file_path, 'w', encoding='utf-8', errors='ignore')
        except Exception as e:
             bot.reply_to(message_obj_for_reply, f"❌ 𝐅ᴀɪʟᴇᴅ ᴛᴏ ᴏᴩᴇɴ ʟᴏɢ ꜰɪʟᴇ: {e}")
             return
        try:
            startupinfo = None; creationflags = 0
            if os.name == 'nt':
                 startupinfo = subprocess.STARTUPINFO(); startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
                 startupinfo.wShowWindow = subprocess.SW_HIDE
            process = subprocess.Popen(
                [sys.executable, script_path], cwd=user_folder, stdout=log_file, stderr=log_file,
                stdin=subprocess.PIPE, startupinfo=startupinfo, creationflags=creationflags,
                encoding='utf-8', errors='ignore'
            )
            bot_scripts[script_key] = {
                'process': process, 'log_file': log_file, 'file_name': file_name,
                'chat_id': message_obj_for_reply.chat.id,
                'script_owner_id': script_owner_id,
                'start_time': datetime.now(), 'user_folder': user_folder, 'type': 'py', 'script_key': script_key
            }
            bot.reply_to(message_obj_for_reply, f"✅ 𝐏ʏᴛʜᴏɴ '{file_name}' 𝐒ᴛᴀʀᴛᴇᴅ! (PID: {process.pid})")
        except FileNotFoundError:
             bot.reply_to(message_obj_for_reply, f"❌ 𝐏ʏᴛʜᴏɴ ɴᴏᴛ ꜰᴏᴜɴᴅ.")
             if log_file and not log_file.closed: log_file.close()
             if script_key in bot_scripts: del bot_scripts[script_key]
        except Exception as e:
            if log_file and not log_file.closed: log_file.close()
            bot.reply_to(message_obj_for_reply, f"❌ 𝐄ʀʀᴏʀ: {str(e)}")
            if process and process.poll() is None:
                 kill_process_tree({'process': process, 'log_file': log_file, 'script_key': script_key})
            if script_key in bot_scripts: del bot_scripts[script_key]
    except Exception as e:
        bot.reply_to(message_obj_for_reply, f"❌ 𝐔ɴᴇxᴩᴇᴄᴛᴇᴅ 𝐄ʀʀᴏʀ: {str(e)}")
        if script_key in bot_scripts:
             kill_process_tree(bot_scripts[script_key])
             del bot_scripts[script_key]

def run_js_script(script_path, script_owner_id, user_folder, file_name, message_obj_for_reply, attempt=1):
    max_attempts = 2
    if attempt > max_attempts:
        bot.reply_to(message_obj_for_reply, f"❌ 𝐅ᴀɪʟᴇᴅ ᴀꜰᴛᴇʀ {max_attempts} ᴀᴛᴛᴇᴍᴩᴛꜱ.")
        return

    script_key = f"{script_owner_id}_{file_name}"
    logger.info(f"Attempt {attempt} JS: {script_path}")

    try:
        if not os.path.exists(script_path):
             bot.reply_to(message_obj_for_reply, f"❌ 𝐒ᴄʀɪᴩᴛ ɴᴏᴛ ꜰᴏᴜɴᴅ!")
             if script_owner_id in user_files:
                 user_files[script_owner_id] = [f for f in user_files.get(script_owner_id, []) if f[0] != file_name]
             remove_user_file_db(script_owner_id, file_name)
             return

        if attempt == 1:
            check_command = ['node', script_path]
            check_proc = None
            try:
                check_proc = subprocess.Popen(check_command, cwd=user_folder, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding='utf-8', errors='ignore')
                stdout, stderr = check_proc.communicate(timeout=5)
                return_code = check_proc.returncode
                if return_code != 0 and stderr:
                    match_js = re.search(r"Cannot find module '(.+?)'", stderr)
                    if match_js:
                        module_name = match_js.group(1).strip().strip("'\"")
                        if not module_name.startswith('.') and not module_name.startswith('/'):
                             if attempt_install_npm(module_name, user_folder, message_obj_for_reply):
                                 time.sleep(2)
                                 threading.Thread(target=run_js_script, args=(script_path, script_owner_id, user_folder, file_name, message_obj_for_reply, attempt + 1)).start()
                                 return
                             else:
                                 bot.reply_to(message_obj_for_reply, f"❌ 𝐍𝐏𝐌 𝐈ɴꜱᴛᴀʟʟ ꜰᴀɪʟᴇᴅ.")
                                 return
                    error_summary = stderr[:500]
                    bot.reply_to(message_obj_for_reply, f"❌ 𝐄ʀʀᴏʀ:\n```\n{error_summary}\n```", parse_mode='Markdown')
                    return
            except subprocess.TimeoutExpired:
                if check_proc and check_proc.poll() is None: check_proc.kill(); check_proc.communicate()
            except FileNotFoundError:
                 bot.reply_to(message_obj_for_reply, "❌ 'node' ɴᴏᴛ ꜰᴏᴜɴᴅ.")
                 return
            except Exception as e:
                 bot.reply_to(message_obj_for_reply, f"❌ 𝐄ʀʀᴏʀ: {e}")
                 return
            finally:
                 if check_proc and check_proc.poll() is None:
                     check_proc.kill(); check_proc.communicate()

        log_file_path = os.path.join(user_folder, f"{os.path.splitext(file_name)[0]}.log")
        log_file = None; process = None
        try: log_file = open(log_file_path, 'w', encoding='utf-8', errors='ignore')
        except Exception as e:
            bot.reply_to(message_obj_for_reply, f"❌ 𝐅ᴀɪʟᴇᴅ ᴛᴏ ᴏᴩᴇɴ ʟᴏɢ: {e}")
            return
        try:
            startupinfo = None; creationflags = 0
            if os.name == 'nt':
                 startupinfo = subprocess.STARTUPINFO(); startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
                 startupinfo.wShowWindow = subprocess.SW_HIDE
            process = subprocess.Popen(
                ['node', script_path], cwd=user_folder, stdout=log_file, stderr=log_file,
                stdin=subprocess.PIPE, startupinfo=startupinfo, creationflags=creationflags,
                encoding='utf-8', errors='ignore'
            )
            bot_scripts[script_key] = {
                'process': process, 'log_file': log_file, 'file_name': file_name,
                'chat_id': message_obj_for_reply.chat.id,
                'script_owner_id': script_owner_id,
                'start_time': datetime.now(), 'user_folder': user_folder, 'type': 'js', 'script_key': script_key
            }
            bot.reply_to(message_obj_for_reply, f"✅ 𝐉𝐒 '{file_name}' 𝐒ᴛᴀʀᴛᴇᴅ! (PID: {process.pid})")
        except FileNotFoundError:
             bot.reply_to(message_obj_for_reply, "❌ 'node' ɴᴏᴛ ꜰᴏᴜɴᴅ.")
             if log_file and not log_file.closed: log_file.close()
             if script_key in bot_scripts: del bot_scripts[script_key]
        except Exception as e:
            if log_file and not log_file.closed: log_file.close()
            bot.reply_to(message_obj_for_reply, f"❌ 𝐄ʀʀᴏʀ: {str(e)}")
            if process and process.poll() is None:
                 kill_process_tree({'process': process, 'log_file': log_file, 'script_key': script_key})
            if script_key in bot_scripts: del bot_scripts[script_key]
    except Exception as e:
        bot.reply_to(message_obj_for_reply, f"❌ 𝐔ɴᴇxᴩᴇᴄᴛᴇᴅ 𝐄ʀʀᴏʀ: {str(e)}")
        if script_key in bot_scripts:
             kill_process_tree(bot_scripts[script_key])
             del bot_scripts[script_key]

TELEGRAM_MODULES = {
    'telebot': 'pyTelegramBotAPI',
    'telegram': 'python-telegram-bot',
    'python_telegram_bot': 'python-telegram-bot',
    'aiogram': 'aiogram',
    'pyrogram': 'pyrogram',
    'telethon': 'telethon',
    'telethon.sync': 'telethon',
    'telepot': 'telepot',
    'pytg': 'pytg',
    'tgcrypto': 'tgcrypto',
    'telegram_upload': 'telegram-upload',
    'telegram_send': 'telegram-send',
    'telegram_text': 'telegram-text',
    'mtproto': 'telegram-mtproto',
    'tl': 'telethon',
    'telegram_utils': 'telegram-utils',
    'telegram_logger': 'telegram-logger',
    'telegram_handlers': 'python-telegram-handlers',
    'telegram_redis': 'telegram-redis',
    'telegram_sqlalchemy': 'telegram-sqlalchemy',
    'telegram_payment': 'telegram-payment',
    'telegram_shop': 'telegram-shop-sdk',
    'pytest_telegram': 'pytest-telegram',
    'telegram_debug': 'telegram-debug',
    'telegram_scraper': 'telegram-scraper',
    'telegram_analytics': 'telegram-analytics',
    'telegram_nlp': 'telegram-nlp-toolkit',
    'telegram_ai': 'telegram-ai',
    'telegram_api': 'telegram-api-client',
    'telegram_web': 'telegram-web-integration',
    'telegram_games': 'telegram-games',
    'telegram_quiz': 'telegram-quiz-bot',
    'telegram_ffmpeg': 'telegram-ffmpeg',
    'telegram_media': 'telegram-media-utils',
    'telegram_2fa': 'telegram-twofa',
    'telegram_crypto': 'telegram-crypto-bot',
    'telegram_i18n': 'telegram-i18n',
    'telegram_translate': 'telegram-translate',
    'bs4': 'beautifulsoup4',
    'requests': 'requests',
    'pillow': 'Pillow',
    'cv2': 'opencv-python',
    'yaml': 'PyYAML',
    'dotenv': 'python-dotenv',
    'dateutil': 'python-dateutil',
    'pandas': 'pandas',
    'numpy': 'numpy',
    'flask': 'Flask',
    'django': 'Django',
    'sqlalchemy': 'SQLAlchemy',
    'asyncio': None, 'json': None, 'datetime': None, 'os': None, 'sys': None,
    're': None, 'time': None, 'math': None, 'random': None, 'logging': None,
    'threading': None, 'subprocess':None, 'zipfile':None, 'tempfile':None,
    'shutil':None, 'sqlite3':None, 'psutil': 'psutil', 'atexit': None
}


# --- Database Operations ---
DB_LOCK = threading.Lock()

def save_user_file(user_id, file_name, file_type='py'):
    with DB_LOCK:
        conn = sqlite3.connect(DATABASE_PATH, check_same_thread=False)
        c = conn.cursor()
        try:
            c.execute('INSERT OR REPLACE INTO user_files (user_id, file_name, file_type) VALUES (?, ?, ?)',
                      (user_id, file_name, file_type))
            conn.commit()
            if user_id not in user_files: user_files[user_id] = []
            user_files[user_id] = [(fn, ft) for fn, ft in user_files[user_id] if fn != file_name]
            user_files[user_id].append((file_name, file_type))
        except Exception as e: logger.error(f"❌ Error saving file: {e}")
        finally: conn.close()

def remove_user_file_db(user_id, file_name):
    with DB_LOCK:
        conn = sqlite3.connect(DATABASE_PATH, check_same_thread=False)
        c = conn.cursor()
        try:
            c.execute('DELETE FROM user_files WHERE user_id = ? AND file_name = ?', (user_id, file_name))
            conn.commit()
            if user_id in user_files:
                user_files[user_id] = [f for f in user_files[user_id] if f[0] != file_name]
                if not user_files[user_id]: del user_files[user_id]
        except Exception as e: logger.error(f"❌ Error removing file: {e}")
        finally: conn.close()

def save_pending_file(user_id, file_name, file_type, message_id):
    with DB_LOCK:
        conn = sqlite3.connect(DATABASE_PATH, check_same_thread=False)
        c = conn.cursor()
        try:
            c.execute('''INSERT OR REPLACE INTO pending_files
                         (user_id, file_name, file_type, upload_time, message_id)
                         VALUES (?, ?, ?, ?, ?)''',
                      (user_id, file_name, file_type, datetime.now().isoformat(), message_id))
            conn.commit()
            if user_id not in pending_approvals:
                pending_approvals[user_id] = []
            pending_approvals[user_id] = [f for f in pending_approvals[user_id] if f[0] != file_name]
            pending_approvals[user_id].append((file_name, file_type, message_id, datetime.now()))
        except Exception as e:
            logger.error(f"Error saving pending file: {e}")
        finally:
            conn.close()

def remove_pending_file(user_id, file_name):
    with DB_LOCK:
        conn = sqlite3.connect(DATABASE_PATH, check_same_thread=False)
        c = conn.cursor()
        try:
            c.execute('DELETE FROM pending_files WHERE user_id = ? AND file_name = ?', (user_id, file_name))
            conn.commit()
            if user_id in pending_approvals:
                pending_approvals[user_id] = [f for f in pending_approvals[user_id] if f[0] != file_name]
                if not pending_approvals[user_id]:
                    del pending_approvals[user_id]
        except Exception as e:
            logger.error(f"Error removing pending: {e}")
        finally:
            conn.close()

def add_active_user(user_id):
    active_users.add(user_id)
    with DB_LOCK:
        conn = sqlite3.connect(DATABASE_PATH, check_same_thread=False)
        c = conn.cursor()
        try:
            c.execute('INSERT OR IGNORE INTO active_users (user_id) VALUES (?)', (user_id,))
            conn.commit()
        except Exception as e: logger.error(f"❌ Error adding user: {e}")
        finally: conn.close()

def save_subscription(user_id, expiry):
    with DB_LOCK:
        conn = sqlite3.connect(DATABASE_PATH, check_same_thread=False)
        c = conn.cursor()
        try:
            expiry_str = expiry.isoformat()
            c.execute('INSERT OR REPLACE INTO subscriptions (user_id, expiry) VALUES (?, ?)', (user_id, expiry_str))
            conn.commit()
            user_subscriptions[user_id] = {'expiry': expiry}
        except Exception as e: logger.error(f"❌ Error saving sub: {e}")
        finally: conn.close()

def remove_subscription_db(user_id):
    with DB_LOCK:
        conn = sqlite3.connect(DATABASE_PATH, check_same_thread=False)
        c = conn.cursor()
        try:
            c.execute('DELETE FROM subscriptions WHERE user_id = ?', (user_id,))
            conn.commit()
            if user_id in user_subscriptions: del user_subscriptions[user_id]
        except Exception as e: logger.error(f"❌ Error removing sub: {e}")
        finally: conn.close()

def add_admin_db(admin_id):
    with DB_LOCK:
        conn = sqlite3.connect(DATABASE_PATH, check_same_thread=False)
        c = conn.cursor()
        try:
            c.execute('INSERT OR IGNORE INTO admins (user_id) VALUES (?)', (admin_id,))
            conn.commit()
            admin_ids.add(admin_id)
        except Exception as e: logger.error(f"❌ Error adding admin: {e}")
        finally: conn.close()

def remove_admin_db(admin_id):
    if admin_id == OWNER_ID:
        return False
    with DB_LOCK:
        conn = sqlite3.connect(DATABASE_PATH, check_same_thread=False)
        c = conn.cursor()
        removed = False
        try:
            c.execute('SELECT 1 FROM admins WHERE user_id = ?', (admin_id,))
            if c.fetchone():
                c.execute('DELETE FROM admins WHERE user_id = ?', (admin_id,))
                conn.commit()
                removed = c.rowcount > 0
                if removed: admin_ids.discard(admin_id)
            else:
                admin_ids.discard(admin_id)
            return removed
        except Exception as e: logger.error(f"❌ Error removing admin: {e}"); return False
        finally: conn.close()

# --- Menu creation (STYLISH) ---
def create_main_menu_inline(user_id):
    markup = types.InlineKeyboardMarkup(row_width=2)
    buttons = [
        types.InlineKeyboardButton('📢 𝐔ᴩᴅᴀᴛᴇꜱ 𝐂ʜᴀɴɴᴇʟ', callback_data='updates_channel'),
        types.InlineKeyboardButton('📤 𝐔ᴩʟᴏᴀᴅ 𝐅ɪʟᴇ', callback_data='upload'),
        types.InlineKeyboardButton('📂 𝐂ʜᴇᴄᴋ 𝐅ɪʟᴇꜱ', callback_data='check_files'),
        types.InlineKeyboardButton('⚡ 𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ', callback_data='speed'),
        types.InlineKeyboardButton('📞 𝐂ᴏɴᴛᴀᴄᴛ 𝐎ᴡɴᴇʀ', url=f'https://t.me/{YOUR_USERNAME.replace("@", "")}')
    ]

    if user_id in admin_ids:
        admin_buttons = [
            types.InlineKeyboardButton('💳 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴꜱ', callback_data='subscription'),
            types.InlineKeyboardButton('📊 𝐒ᴛᴀᴛɪꜱᴛɪᴄꜱ', callback_data='stats'),
            types.InlineKeyboardButton('🔒 𝐋ᴏᴄᴋ 𝐁ᴏᴛ' if not bot_locked else '🔓 𝐔ɴʟᴏᴄᴋ 𝐁ᴏᴛ',
                                     callback_data='lock_bot' if not bot_locked else 'unlock_bot'),
            types.InlineKeyboardButton('📢 𝐁ʀᴏᴀᴅᴄᴀꜱᴛ', callback_data='broadcast'),
            types.InlineKeyboardButton('👑 𝐀ᴅᴍɪɴ 𝐏ᴀɴᴇʟ', callback_data='admin_panel'),
            types.InlineKeyboardButton('🟢 𝐑ᴜɴ 𝐀ʟʟ 𝐔ꜱᴇʀ 𝐒ᴄʀɪᴩᴛꜱ', callback_data='run_all_scripts'),
            types.InlineKeyboardButton('⏳ 𝐏ᴇɴᴅɪɴɢ 𝐅ɪʟᴇꜱ', callback_data='pending_files')
        ]
        markup.add(buttons[0])
        markup.add(buttons[1], buttons[2])
        markup.add(buttons[3], admin_buttons[0])
        markup.add(admin_buttons[1], admin_buttons[3])
        markup.add(admin_buttons[2], admin_buttons[5])
        markup.add(admin_buttons[6])
        markup.add(admin_buttons[4])
        markup.add(buttons[4])
    else:
        markup.add(buttons[0])
        markup.add(buttons[1], buttons[2])
        markup.add(buttons[3])
        markup.add(types.InlineKeyboardButton('📊 𝐒ᴛᴀᴛɪꜱᴛɪᴄꜱ', callback_data='stats'))
        markup.add(buttons[4])
    return markup

def create_reply_keyboard_main_menu(user_id):
    markup = types.ReplyKeyboardMarkup(resize_keyboard=True, row_width=2)
    layout_to_use = ADMIN_COMMAND_BUTTONS_LAYOUT_USER_SPEC if user_id in admin_ids else COMMAND_BUTTONS_LAYOUT_USER_SPEC
    for row_buttons_text in layout_to_use:
        markup.add(*[types.KeyboardButton(text) for text in row_buttons_text])
    return markup

def create_control_buttons(script_owner_id, file_name, is_running=True):
    markup = types.InlineKeyboardMarkup(row_width=2)
    if is_running:
        markup.row(
            types.InlineKeyboardButton("🔴 𝐒ᴛᴏᴩ", callback_data=f'stop_{script_owner_id}_{file_name}'),
            types.InlineKeyboardButton("🔄 𝐑ᴇꜱᴛᴀʀᴛ", callback_data=f'restart_{script_owner_id}_{file_name}')
        )
        markup.row(
            types.InlineKeyboardButton("🗑️ 𝐃ᴇʟᴇᴛᴇ", callback_data=f'delete_{script_owner_id}_{file_name}'),
            types.InlineKeyboardButton("📜 𝐋ᴏɢꜱ", callback_data=f'logs_{script_owner_id}_{file_name}')
        )
    else:
        markup.row(
            types.InlineKeyboardButton("🟢 𝐒ᴛᴀʀᴛ", callback_data=f'start_{script_owner_id}_{file_name}'),
            types.InlineKeyboardButton("🗑️ 𝐃ᴇʟᴇᴛᴇ", callback_data=f'delete_{script_owner_id}_{file_name}')
        )
        markup.row(
            types.InlineKeyboardButton("📜 𝐕ɪᴇᴡ 𝐋ᴏɢꜱ", callback_data=f'logs_{script_owner_id}_{file_name}')
        )
    markup.add(types.InlineKeyboardButton("🔙 𝐁ᴀᴄᴋ ᴛᴏ 𝐅ɪʟᴇꜱ", callback_data='check_files'))
    return markup

def create_admin_panel():
    markup = types.InlineKeyboardMarkup(row_width=2)
    markup.row(
        types.InlineKeyboardButton('➕ 𝐀ᴅᴅ 𝐀ᴅᴍɪɴ', callback_data='add_admin'),
        types.InlineKeyboardButton('➖ 𝐑ᴇᴍᴏᴠᴇ 𝐀ᴅᴍɪɴ', callback_data='remove_admin')
    )
    markup.row(types.InlineKeyboardButton('📋 𝐋ɪꜱᴛ 𝐀ᴅᴍɪɴꜱ', callback_data='list_admins'))
    markup.row(types.InlineKeyboardButton('🔙 𝐁ᴀᴄᴋ ᴛᴏ 𝐌ᴀɪɴ', callback_data='back_to_main'))
    return markup

def create_subscription_menu():
    markup = types.InlineKeyboardMarkup(row_width=2)
    markup.row(
        types.InlineKeyboardButton('➕ 𝐀ᴅᴅ 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴ', callback_data='add_subscription'),
        types.InlineKeyboardButton('➖ 𝐑ᴇᴍᴏᴠᴇ 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴ', callback_data='remove_subscription')
    )
    markup.row(types.InlineKeyboardButton('🔍 𝐂ʜᴇᴄᴋ 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴ', callback_data='check_subscription'))
    markup.row(types.InlineKeyboardButton('🔙 𝐁ᴀᴄᴋ ᴛᴏ 𝐌ᴀɪɴ', callback_data='back_to_main'))
    return markup

# --- File Handling ---
def handle_zip_file(downloaded_file_content, file_name_zip, message):
    user_id = message.from_user.id
    user_folder = get_user_folder(user_id)
    temp_dir = None
    try:
        temp_dir = tempfile.mkdtemp(prefix=f"user_{user_id}_zip_")
        zip_path = os.path.join(temp_dir, file_name_zip)
        with open(zip_path, 'wb') as new_file: new_file.write(downloaded_file_content)
        with zipfile.ZipFile(zip_path, 'r') as zip_ref:
            for member in zip_ref.infolist():
                member_path = os.path.abspath(os.path.join(temp_dir, member.filename))
                if not member_path.startswith(os.path.abspath(temp_dir)):
                    raise zipfile.BadZipFile(f"Zip has unsafe path: {member.filename}")
            zip_ref.extractall(temp_dir)

        extracted_items = os.listdir(temp_dir)
        py_files = [f for f in extracted_items if f.endswith('.py')]
        js_files = [f for f in extracted_items if f.endswith('.js')]
        req_file = 'requirements.txt' if 'requirements.txt' in extracted_items else None
        pkg_json = 'package.json' if 'package.json' in extracted_items else None

        if req_file:
            req_path = os.path.join(temp_dir, req_file)
            bot.reply_to(message, f"🔄 𝐈ɴꜱᴛᴀʟʟɪɴɢ 𝐏ʏᴛʜᴏɴ ᴅᴇᴩꜱ...")
            try:
                command = [sys.executable, '-m', 'pip', 'install', '-r', req_path]
                result = subprocess.run(command, capture_output=True, text=True, check=True, encoding='utf-8', errors='ignore')
                bot.reply_to(message, f"✅ 𝐏ʏᴛʜᴏɴ ᴅᴇᴩꜱ 𝐈ɴꜱᴛᴀʟʟᴇᴅ.")
            except subprocess.CalledProcessError as e:
                error_msg = f"❌ 𝐅ᴀɪʟᴇᴅ ᴛᴏ 𝐈ɴꜱᴛᴀʟʟ 𝐏ʏᴛʜᴏɴ ᴅᴇᴩꜱ.\n```\n{e.stderr or e.stdout}\n```"
                if len(error_msg) > 4000: error_msg = error_msg[:4000]
                bot.reply_to(message, error_msg, parse_mode='Markdown'); return
            except Exception as e:
                 bot.reply_to(message, f"❌ 𝐄ʀʀᴏʀ: {e}"); return

        if pkg_json:
            bot.reply_to(message, f"🔄 𝐈ɴꜱᴛᴀʟʟɪɴɢ 𝐍ᴏᴅᴇ ᴅᴇᴩꜱ...")
            try:
                command = ['npm', 'install']
                result = subprocess.run(command, capture_output=True, text=True, check=True, cwd=temp_dir, encoding='utf-8', errors='ignore')
                bot.reply_to(message, f"✅ 𝐍ᴏᴅᴇ ᴅᴇᴩꜱ 𝐈ɴꜱᴛᴀʟʟᴇᴅ.")
            except FileNotFoundError:
                bot.reply_to(message, "❌ 'npm' ɴᴏᴛ ꜰᴏᴜɴᴅ."); return
            except subprocess.CalledProcessError as e:
                error_msg = f"❌ 𝐅ᴀɪʟᴇᴅ ᴛᴏ 𝐈ɴꜱᴛᴀʟʟ 𝐍ᴏᴅᴇ ᴅᴇᴩꜱ.\n```\n{e.stderr or e.stdout}\n```"
                if len(error_msg) > 4000: error_msg = error_msg[:4000]
                bot.reply_to(message, error_msg, parse_mode='Markdown'); return
            except Exception as e:
                 bot.reply_to(message, f"❌ 𝐄ʀʀᴏʀ: {e}"); return

        main_script_name = None; file_type = None
        preferred_py = ['main.py', 'bot.py', 'app.py']; preferred_js = ['index.js', 'main.js', 'bot.js', 'app.js']
        for p in preferred_py:
            if p in py_files: main_script_name = p; file_type = 'py'; break
        if not main_script_name:
             for p in preferred_js:
                 if p in js_files: main_script_name = p; file_type = 'js'; break
        if not main_script_name:
            if py_files: main_script_name = py_files[0]; file_type = 'py'
            elif js_files: main_script_name = js_files[0]; file_type = 'js'
        if not main_script_name:
            bot.reply_to(message, "❌ 𝐍ᴏ `.py` ᴏʀ `.js` ꜱᴄʀɪᴩᴛ ꜰᴏᴜɴᴅ!"); return

        for item_name in os.listdir(temp_dir):
            src_path = os.path.join(temp_dir, item_name)
            dest_path = os.path.join(user_folder, item_name)
            if os.path.isdir(dest_path): shutil.rmtree(dest_path)
            elif os.path.exists(dest_path): os.remove(dest_path)
            shutil.move(src_path, dest_path)

        if ADMIN_APPROVAL_REQUIRED and user_id not in admin_ids:
            save_pending_file(user_id, main_script_name, file_type, message.message_id)
            bot.reply_to(
                message,
                f"✅ 𝐙𝐈𝐏 𝐄xᴛʀᴀᴄᴛᴇᴅ!\n📁 𝐌ᴀɪɴ: `{main_script_name}` ({file_type})\n\n"
                f"⚠️ **𝐀ᴅᴍɪɴ 𝐀ᴩᴩʀᴏᴠᴀʟ 𝐑ᴇQᴜɪʀᴇᴅ** ʙᴇꜰᴏʀᴇ ʜᴏꜱᴛɪɴɢ.",
                parse_mode='Markdown'
            )
            notify_admins_for_approval(user_id, main_script_name, file_type, message)
            return

        save_user_file(user_id, main_script_name, file_type)
        main_script_path = os.path.join(user_folder, main_script_name)
        bot.reply_to(message, f"✅ 𝐒ᴛᴀʀᴛɪɴɢ `{main_script_name}`...", parse_mode='Markdown')

        if file_type == 'py':
             threading.Thread(target=run_script, args=(main_script_path, user_id, user_folder, main_script_name, message)).start()
        elif file_type == 'js':
             threading.Thread(target=run_js_script, args=(main_script_path, user_id, user_folder, main_script_name, message)).start()

    except zipfile.BadZipFile as e:
        bot.reply_to(message, f"❌ 𝐈ɴᴠᴀʟɪᴅ 𝐙𝐈𝐏: {e}")
    except Exception as e:
        bot.reply_to(message, f"❌ 𝐄ʀʀᴏʀ: {str(e)}")
    finally:
        if temp_dir and os.path.exists(temp_dir):
            try: shutil.rmtree(temp_dir)
            except Exception as e: logger.error(f"Failed to clean temp: {e}")

def handle_js_file(file_path, script_owner_id, user_folder, file_name, message):
    try:
        save_user_file(script_owner_id, file_name, 'js')
        threading.Thread(target=run_js_script, args=(file_path, script_owner_id, user_folder, file_name, message)).start()
    except Exception as e:
        bot.reply_to(message, f"❌ 𝐄ʀʀᴏʀ: {str(e)}")

def handle_py_file(file_path, script_owner_id, user_folder, file_name, message):
    try:
        save_user_file(script_owner_id, file_name, 'py')
        threading.Thread(target=run_script, args=(file_path, script_owner_id, user_folder, file_name, message)).start()
    except Exception as e:
        bot.reply_to(message, f"❌ 𝐄ʀʀᴏʀ: {str(e)}")

def notify_admins_for_approval(user_id, file_name, file_type, original_message):
    try:
        user_info = bot.get_chat(user_id)
        user_name = user_info.first_name or "Unknown"
        user_username = user_info.username or "NoUsername"

        approval_text = (
            f"🔔 **𝐍𝐄𝐖 𝐅𝐈𝐋𝐄 𝐀𝐏𝐏𝐑𝐎𝐕𝐀𝐋 𝐑𝐄𝐐𝐔𝐄𝐒𝐓**\n\n"
            f"👤 𝐔ꜱᴇʀ: {user_name} (@{user_username})\n"
            f"🆔 𝐔ꜱᴇʀ 𝐈𝐃: `{user_id}`\n"
            f"📁 𝐅ɪʟᴇ: `{file_name}`\n"
            f"📝 𝐓ʏᴩᴇ: `{file_type}`\n"
            f"⏰ 𝐓ɪᴍᴇ: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n"
            f"⏳ 𝐖ᴀɪᴛɪɴɢ ꜰᴏʀ ᴀᴩᴩʀᴏᴠᴀʟ..."
        )

        markup = types.InlineKeyboardMarkup(row_width=2)
        markup.row(
            types.InlineKeyboardButton("✅ 𝐀ᴩᴩʀᴏᴠᴇ & 𝐑ᴜɴ", callback_data=f'approve_{user_id}_{file_name}'),
            types.InlineKeyboardButton("❌ 𝐑ᴇᴊᴇᴄᴛ", callback_data=f'reject_{user_id}_{file_name}')
        )

        for admin_id in admin_ids:
            try:
                bot.send_message(admin_id, approval_text, reply_markup=markup, parse_mode='Markdown')
            except Exception as e:
                logger.error(f"Failed to notify admin {admin_id}: {e}")
    except Exception as e:
        logger.error(f"Error notifying admins: {e}", exc_info=True)


# --- Logic Functions ---
def _logic_send_welcome(message):
    user_id = message.from_user.id
    chat_id = message.chat.id
    user_name = message.from_user.first_name
    user_username = message.from_user.username

    if bot_locked and user_id not in admin_ids:
        bot.send_message(chat_id, "⚠️ 𝐁ᴏᴛ ʟᴏᴄᴋᴇᴅ ʙʏ ᴀᴅᴍɪɴ. 𝐓ʀʏ ʟᴀᴛᴇʀ.")
        return

    user_bio = "Could not fetch bio"; photo_file_id = None
    try: user_bio = bot.get_chat(user_id).bio or "No bio"
    except Exception: pass
    try:
        user_profile_photos = bot.get_user_profile_photos(user_id, limit=1)
        if user_profile_photos.photos: photo_file_id = user_profile_photos.photos[0][-1].file_id
    except Exception: pass

    if user_id not in active_users:
        add_active_user(user_id)
        try:
            owner_notification = (f"🎉 𝐍ᴇᴡ 𝐔ꜱᴇʀ!\n👤 𝐍ᴀᴍᴇ: {user_name}\n✳️ 𝐔ꜱᴇʀ: @{user_username or 'N/A'}\n"
                                  f"🆔 𝐈𝐃: `{user_id}`\n📝 𝐁ɪᴏ: {user_bio}")
            bot.send_message(OWNER_ID, owner_notification, parse_mode='Markdown')
            if photo_file_id: bot.send_photo(OWNER_ID, photo_file_id, caption=f"Pic of new user {user_id}")
        except Exception as e: logger.error(f"⚠️ Failed to notify owner: {e}")

    file_limit = get_user_file_limit(user_id)
    current_files = get_user_file_count(user_id)
    limit_str = str(file_limit) if file_limit != float('inf') else "Unlimited"
    expiry_info = ""
    if user_id == OWNER_ID: user_status = "👑 𝐎ᴡɴᴇʀ"
    elif user_id in admin_ids: user_status = "🛡️ 𝐀ᴅᴍɪɴ"
    elif user_id in user_subscriptions:
        expiry_date = user_subscriptions[user_id].get('expiry')
        if expiry_date and expiry_date > datetime.now():
            user_status = "⭐ 𝐏ʀᴇᴍɪᴜᴍ"; days_left = (expiry_date - datetime.now()).days
            expiry_info = f"\n⏳ 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴ ᴇxᴩɪʀᴇꜱ ɪɴ: {days_left} ᴅᴀʏꜱ"
        else: user_status = "🆓 𝐅ʀᴇᴇ 𝐔ꜱᴇʀ (𝐄xᴩɪʀᴇᴅ)"; remove_subscription_db(user_id)
    else: user_status = "🆓 𝐅ʀᴇᴇ 𝐔ꜱᴇʀ"

    welcome_msg_text = (f"〽️ 𝐖ᴇʟᴄᴏᴍᴇ, {user_name}!\n\n🆔 𝐘ᴏᴜʀ 𝐔ꜱᴇʀ 𝐈𝐃: `{user_id}`\n"
                        f"✳️ 𝐔ꜱᴇʀɴᴀᴍᴇ: `@{user_username or 'Not set'}`\n"
                        f"🔰 𝐘ᴏᴜʀ 𝐒ᴛᴀᴛᴜꜱ: {user_status}{expiry_info}\n"
                        f"📁 𝐅ɪʟᴇꜱ 𝐔ᴩʟᴏᴀᴅᴇᴅ: {current_files} / {limit_str}\n\n"
                        f"🤖 𝐇ᴏꜱᴛ & ʀᴜɴ 𝐏ʏᴛʜᴏɴ (`.py`) ᴏʀ 𝐉𝐒 (`.js`) ꜱᴄʀɪᴩᴛꜱ.\n"
                        f"   𝐔ᴩʟᴏᴀᴅ ꜱɪɴɢʟᴇ ꜱᴄʀɪᴩᴛꜱ ᴏʀ `.zip` ᴀʀᴄʜɪᴠᴇꜱ.\n\n"
                        f"👇 𝐔ꜱᴇ ʙᴜᴛᴛᴏɴꜱ ᴏʀ ᴛʏᴩᴇ ᴄᴏᴍᴍᴀɴᴅꜱ.")
    main_reply_markup = create_reply_keyboard_main_menu(user_id)
    try:
        if photo_file_id: bot.send_photo(chat_id, photo_file_id)
        bot.send_message(chat_id, welcome_msg_text, reply_markup=main_reply_markup, parse_mode='Markdown')
    except Exception as e:
        logger.error(f"Error sending welcome: {e}", exc_info=True)
        try: bot.send_message(chat_id, welcome_msg_text, reply_markup=main_reply_markup, parse_mode='Markdown')
        except Exception as fallback_e: logger.error(f"Fallback failed: {fallback_e}")

def _logic_updates_channel(message):
    channel_username = UPDATE_CHANNEL.replace('@', '')
    channel_link = f"https://t.me/{channel_username}"
    text = (
        f"📢 **𝐎ꜰꜰɪᴄɪᴀʟ 𝐔ᴩᴅᴀᴛᴇꜱ 𝐂ʜᴀɴɴᴇʟ**\n\n"
        f"🔗 **𝐋ɪɴᴋ:** {channel_link}\n"
        f"📛 **𝐔ꜱᴇʀɴᴀᴍᴇ:** `@{channel_username}`\n\n"
        f"👆 𝐔ᴩᴀʀ ᴡᴀʟᴇ ʟɪɴᴋ ᴩᴇ ᴄʟɪᴄᴋ ᴋᴀʀᴏ ʏᴀ ᴄᴏᴩʏ ᴋᴀʀᴋᴇ 𝐓ᴇʟᴇɢʀᴀᴍ ᴍᴇ ᴏᴩᴇɴ ᴋᴀʀᴏ.\n\n"
        f"📌 𝐘ᴀʜᴀɴ ᴀᴀᴩᴋᴏ ᴍɪʟᴇɴɢᴇ:\n"
        f"• 𝐁ᴏᴛ ᴋᴇ ɴᴀʏᴇ ꜰᴇᴀᴛᴜʀᴇꜱ\n"
        f"• 𝐁ᴜɢ ꜰɪxᴇꜱ & ᴜᴩᴅᴀᴛᴇꜱ\n"
        f"• 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴ ᴩʟᴀɴꜱ\n"
        f"• 𝐂ᴏɴᴛᴀᴄᴛ ᴅᴇᴛᴀɪʟꜱ"
    )
    markup = types.InlineKeyboardMarkup()
    markup.add(types.InlineKeyboardButton('📢 𝐎ᴩᴇɴ 𝐂ʜᴀɴɴᴇʟ', url=channel_link))
    bot.reply_to(message, text, reply_markup=markup, parse_mode='Markdown')

def _logic_upload_file(message):
    user_id = message.from_user.id
    if bot_locked and user_id not in admin_ids:
        bot.reply_to(message, "⚠️ 𝐁ᴏᴛ ʟᴏᴄᴋᴇᴅ, ᴄᴀɴɴᴏᴛ ᴀᴄᴄᴇᴩᴛ ꜰɪʟᴇꜱ.")
        return
    file_limit = get_user_file_limit(user_id)
    current_files = get_user_file_count(user_id)
    if current_files >= file_limit:
        limit_str = str(file_limit) if file_limit != float('inf') else "Unlimited"
        bot.reply_to(message, f"⚠️ 𝐅ɪʟᴇ ʟɪᴍɪᴛ ({current_files}/{limit_str}) ʀᴇᴀᴄʜᴇᴅ.")
        return
    bot.reply_to(message, "📤 𝐒ᴇɴᴅ ʏᴏᴜʀ 𝐏ʏᴛʜᴏɴ (`.py`), 𝐉𝐒 (`.js`), ᴏʀ 𝐙𝐈𝐏 (`.zip`) ꜰɪʟᴇ.\n\n⚠️ 𝐍ᴏᴛᴇ: 𝐀ᴅᴍɪɴ ᴀᴩᴩʀᴏᴠᴀʟ ʀᴇQᴜɪʀᴇᴅ.")

def _logic_check_files(message):
    user_id = message.from_user.id
    user_files_list = user_files.get(user_id, [])
    pending_list = pending_approvals.get(user_id, [])

    if not user_files_list and not pending_list:
        bot.reply_to(message, "📂 𝐘ᴏᴜʀ ꜰɪʟᴇꜱ:\n\n(𝐍ᴏ ꜰɪʟᴇꜱ ᴜᴩʟᴏᴀᴅᴇᴅ ʏᴇᴛ)")
        return

    text = "📂 **𝐘ᴏᴜʀ 𝐅ɪʟᴇꜱ:**\n\n"

    if pending_list:
        text += "⏳ **𝐏ᴇɴᴅɪɴɢ 𝐀ᴩᴩʀᴏᴠᴀʟ:**\n"
        for f in pending_list:
            text += f"  • `{f[0]}` ({f[1]}) - ⏳ 𝐖ᴀɪᴛɪɴɢ\n"
        text += "\n"

    if user_files_list:
        text += "✅ **𝐀ᴄᴛɪᴠᴇ 𝐅ɪʟᴇꜱ:**\n"
        markup = types.InlineKeyboardMarkup(row_width=1)
        for file_name, file_type in sorted(user_files_list):
            is_running = is_bot_running(user_id, file_name)
            status_icon = "🟢 𝐑ᴜɴɴɪɴɢ" if is_running else "🔴 𝐒ᴛᴏᴩᴩᴇᴅ"
            btn_text = f"{file_name} ({file_type}) - {status_icon}"
            markup.add(types.InlineKeyboardButton(btn_text, callback_data=f'file_{user_id}_{file_name}'))
        bot.reply_to(message, text, reply_markup=markup, parse_mode='Markdown')
    else:
        bot.reply_to(message, text, parse_mode='Markdown')

def _logic_bot_speed(message):
    user_id = message.from_user.id
    chat_id = message.chat.id
    start_time_ping = time.time()
    wait_msg = bot.reply_to(message, "🏃 𝐓ᴇꜱᴛɪɴɢ ꜱᴩᴇᴇᴅ...")
    try:
        bot.send_chat_action(chat_id, 'typing')
        response_time = round((time.time() - start_time_ping) * 1000, 2)
        status = "🔓 𝐔ɴʟᴏᴄᴋᴇᴅ" if not bot_locked else "🔒 𝐋ᴏᴄᴋᴇᴅ"
        if user_id == OWNER_ID: user_level = "👑 𝐎ᴡɴᴇʀ"
        elif user_id in admin_ids: user_level = "🛡️ 𝐀ᴅᴍɪɴ"
        elif user_id in user_subscriptions and user_subscriptions[user_id].get('expiry', datetime.min) > datetime.now(): user_level = "⭐ 𝐏ʀᴇᴍɪᴜᴍ"
        else: user_level = "🆓 𝐅ʀᴇᴇ 𝐔ꜱᴇʀ"
        speed_msg = (f"⚡ 𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ & 𝐒ᴛᴀᴛᴜꜱ:\n\n⏱️ 𝐀𝐏𝐈 𝐑ᴇꜱᴩᴏɴꜱᴇ 𝐓ɪᴍᴇ: {response_time} ᴍꜱ\n"
                     f"🚦 𝐁ᴏᴛ 𝐒ᴛᴀᴛᴜꜱ: {status}\n"
                     f"👤 𝐘ᴏᴜʀ 𝐋ᴇᴠᴇʟ: {user_level}")
        bot.edit_message_text(speed_msg, chat_id, wait_msg.message_id)
    except Exception as e:
        logger.error(f"Error during speed test: {e}", exc_info=True)
        bot.edit_message_text("❌ 𝐄ʀʀᴏʀ ᴅᴜʀɪɴɢ ꜱᴩᴇᴇᴅ ᴛᴇꜱᴛ.", chat_id, wait_msg.message_id)

def _logic_contact_owner(message):
    markup = types.InlineKeyboardMarkup()
    markup.add(types.InlineKeyboardButton('📞 𝐂ᴏɴᴛᴀᴄᴛ 𝐎ᴡɴᴇʀ', url=f'https://t.me/{YOUR_USERNAME.replace("@", "")}'))
    bot.reply_to(message, "𝐂ʟɪᴄᴋ ᴛᴏ ᴄᴏɴᴛᴀᴄᴛ 𝐎ᴡɴᴇʀ:", reply_markup=markup)

def _logic_subscriptions_panel(message):
    if message.from_user.id not in admin_ids:
        bot.reply_to(message, "⚠️ 𝐀ᴅᴍɪɴ ᴩᴇʀᴍɪꜱꜱɪᴏɴꜱ ʀᴇQᴜɪʀᴇᴅ.")
        return
    bot.reply_to(message, "💳 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴ 𝐌ᴀɴᴀɢᴇᴍᴇɴᴛ", reply_markup=create_subscription_menu())

def _logic_statistics(message):
    user_id = message.from_user.id
    total_users = len(active_users)
    total_files_records = sum(len(files) for files in user_files.values())
    total_pending = sum(len(files) for files in pending_approvals.values())

    running_bots_count = 0
    user_running_bots = 0

    for script_key_iter, script_info_iter in list(bot_scripts.items()):
        s_owner_id, _ = script_key_iter.split('_', 1)
        if is_bot_running(int(s_owner_id), script_info_iter['file_name']):
            running_bots_count += 1
            if int(s_owner_id) == user_id:
                user_running_bots +=1

    stats_msg_base = (f"📊 𝐁ᴏᴛ 𝐒ᴛᴀᴛɪꜱᴛɪᴄꜱ:\n\n"
                      f"👥 𝐓ᴏᴛᴀʟ 𝐔ꜱᴇʀꜱ: {total_users}\n"
                      f"📂 𝐓ᴏᴛᴀʟ 𝐅ɪʟᴇ 𝐑ᴇᴄᴏʀᴅꜱ: {total_files_records}\n"
                      f"🟢 𝐓ᴏᴛᴀʟ 𝐀ᴄᴛɪᴠᴇ 𝐁ᴏᴛꜱ: {running_bots_count}\n")

    if user_id in admin_ids:
        stats_msg_admin = (f"🔒 𝐁ᴏᴛ 𝐒ᴛᴀᴛᴜꜱ: {'🔴 𝐋ᴏᴄᴋᴇᴅ' if bot_locked else '🟢 𝐔ɴʟᴏᴄᴋᴇᴅ'}\n"
                           f"⏳ 𝐏ᴇɴᴅɪɴɢ 𝐀ᴩᴩʀᴏᴠᴀʟꜱ: {total_pending}\n"
                           f"🤖 𝐘ᴏᴜʀ 𝐑ᴜɴɴɪɴɢ 𝐁ᴏᴛꜱ: {user_running_bots}")
        stats_msg = stats_msg_base + stats_msg_admin
    else:
        stats_msg = stats_msg_base + f"🤖 𝐘ᴏᴜʀ 𝐑ᴜɴɴɪɴɢ 𝐁ᴏᴛꜱ: {user_running_bots}"

    bot.reply_to(message, stats_msg)


def _logic_broadcast_init(message):
    if message.from_user.id not in admin_ids:
        bot.reply_to(message, "⚠️ 𝐀ᴅᴍɪɴ ᴩᴇʀᴍɪꜱꜱɪᴏɴꜱ ʀᴇQᴜɪʀᴇᴅ.")
        return
    msg = bot.reply_to(message, "📢 𝐒ᴇɴᴅ ᴍᴇꜱꜱᴀɢᴇ ᴛᴏ ʙʀᴏᴀᴅᴄᴀꜱᴛ ᴛᴏ ᴀʟʟ ᴀᴄᴛɪᴠᴇ ᴜꜱᴇʀꜱ.\n/cancel ᴛᴏ ᴀʙᴏʀᴛ.")
    bot.register_next_step_handler(msg, process_broadcast_message)

def _logic_toggle_lock_bot(message):
    if message.from_user.id not in admin_ids:
        bot.reply_to(message, "⚠️ 𝐀ᴅᴍɪɴ ᴩᴇʀᴍɪꜱꜱɪᴏɴꜱ ʀᴇQᴜɪʀᴇᴅ.")
        return
    global bot_locked
    bot_locked = not bot_locked
    status = "ʟᴏᴄᴋᴇᴅ" if bot_locked else "ᴜɴʟᴏᴄᴋᴇᴅ"
    logger.warning(f"Bot {status} by Admin {message.from_user.id}")
    bot.reply_to(message, f"🔒 𝐁ᴏᴛ ʜᴀꜱ ʙᴇᴇɴ {status}.")

def _logic_admin_panel(message):
    if message.from_user.id not in admin_ids:
        bot.reply_to(message, "⚠️ 𝐀ᴅᴍɪɴ ᴩᴇʀᴍɪꜱꜱɪᴏɴꜱ ʀᴇQᴜɪʀᴇᴅ.")
        return
    bot.reply_to(message, "👑 𝐀ᴅᴍɪɴ 𝐏ᴀɴᴇʟ\n𝐌ᴀɴᴀɢᴇ ᴀᴅᴍɪɴꜱ.",
                 reply_markup=create_admin_panel())

def _logic_pending_files(message):
    if message.from_user.id not in admin_ids:
        bot.reply_to(message, "⚠️ 𝐀ᴅᴍɪɴ ᴩᴇʀᴍɪꜱꜱɪᴏɴꜱ ʀᴇQᴜɪʀᴇᴅ.")
        return
    if not pending_approvals:
        bot.reply_to(message, "✅ 𝐍ᴏ ᴩᴇɴᴅɪɴɢ ꜰɪʟᴇꜱ.")
        return
    text = "📋 **𝐏ᴇɴᴅɪɴɢ 𝐀ᴩᴩʀᴏᴠᴀʟꜱ:**\n\n"
    markup = types.InlineKeyboardMarkup(row_width=2)
    for uid, files in pending_approvals.items():
        text += f"👤 𝐔ꜱᴇʀ `{uid}`:\n"
        for f in files:
            text += f"  • `{f[0]}` ({f[1]})\n"
            markup.row(
                types.InlineKeyboardButton(f"✅ {f[0][:20]}", callback_data=f'approve_{uid}_{f[0]}'),
                types.InlineKeyboardButton(f"❌ 𝐑ᴇᴊᴇᴄᴛ", callback_data=f'reject_{uid}_{f[0]}')
            )
        text += "\n"
    bot.reply_to(message, text, reply_markup=markup, parse_mode='Markdown')

def _logic_run_all_scripts(message_or_call):
    if isinstance(message_or_call, telebot.types.Message):
        admin_user_id = message_or_call.from_user.id
        admin_chat_id = message_or_call.chat.id
        reply_func = lambda text, **kwargs: bot.reply_to(message_or_call, text, **kwargs)
        admin_message_obj_for_script_runner = message_or_call
    elif isinstance(message_or_call, telebot.types.CallbackQuery):
        admin_user_id = message_or_call.from_user.id
        admin_chat_id = message_or_call.message.chat.id
        bot.answer_callback_query(message_or_call.id)
        reply_func = lambda text, **kwargs: bot.send_message(admin_chat_id, text, **kwargs)
        admin_message_obj_for_script_runner = message_or_call.message
    else:
        return

    if admin_user_id not in admin_ids:
        reply_func("⚠️ 𝐀ᴅᴍɪɴ ᴩᴇʀᴍɪꜱꜱɪᴏɴꜱ ʀᴇQᴜɪʀᴇᴅ.")
        return

    reply_func("⏳ 𝐒ᴛᴀʀᴛɪɴɢ ᴩʀᴏᴄᴇꜱꜱ ᴛᴏ ʀᴜɴ ᴀʟʟ ᴜꜱᴇʀ ꜱᴄʀɪᴩᴛꜱ...")

    started_count = 0; attempted_users = 0; skipped_files = 0; error_files_details = []
    all_user_files_snapshot = dict(user_files)

    for target_user_id, files_for_user in all_user_files_snapshot.items():
        if not files_for_user: continue
        attempted_users += 1
        user_folder = get_user_folder(target_user_id)

        for file_name, file_type in files_for_user:
            if not is_bot_running(target_user_id, file_name):
                file_path = os.path.join(user_folder, file_name)
                if os.path.exists(file_path):
                    try:
                        if file_type == 'py':
                            threading.Thread(target=run_script, args=(file_path, target_user_id, user_folder, file_name, admin_message_obj_for_script_runner)).start()
                            started_count += 1
                        elif file_type == 'js':
                            threading.Thread(target=run_js_script, args=(file_path, target_user_id, user_folder, file_name, admin_message_obj_for_script_runner)).start()
                            started_count += 1
                        else:
                            error_files_details.append(f"`{file_name}` - Unknown type")
                            skipped_files += 1
                        time.sleep(0.7)
                    except Exception as e:
                        error_files_details.append(f"`{file_name}` - Error")
                        skipped_files += 1
                else:
                    error_files_details.append(f"`{file_name}` - Not found")
                    skipped_files += 1

    summary_msg = (f"✅ 𝐀ʟʟ 𝐔ꜱᴇʀꜱ' 𝐒ᴄʀɪᴩᴛꜱ - 𝐂ᴏᴍᴩʟᴇᴛᴇ:\n\n"
                   f"▶️ 𝐒ᴛᴀʀᴛᴇᴅ: {started_count} ꜱᴄʀɪᴩᴛꜱ.\n"
                   f"👥 𝐔ꜱᴇʀꜱ ᴩʀᴏᴄᴇꜱꜱᴇᴅ: {attempted_users}.\n")
    if skipped_files > 0:
        summary_msg += f"⚠️ 𝐒ᴋɪᴩᴩᴇᴅ/𝐄ʀʀᴏʀ: {skipped_files}\n"
        if error_files_details:
             summary_msg += "𝐃ᴇᴛᴀɪʟꜱ:\n" + "\n".join([f"  - {err}" for err in error_files_details[:5]])

    reply_func(summary_msg, parse_mode='Markdown')


# --- Command Handlers ---
@bot.message_handler(commands=['start', 'help'])
def command_send_welcome(message): _logic_send_welcome(message)

@bot.message_handler(commands=['status'])
def command_show_status(message): _logic_statistics(message)

BUTTON_TEXT_TO_LOGIC = {
    "📢 𝐔ᴩᴅᴀᴛᴇꜱ 𝐂ʜᴀɴɴᴇʟ": _logic_updates_channel,
    "📤 𝐔ᴩʟᴏᴀᴅ 𝐅ɪʟᴇ": _logic_upload_file,
    "📂 𝐂ʜᴇᴄᴋ 𝐅ɪʟᴇꜱ": _logic_check_files,
    "⚡ 𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ": _logic_bot_speed,
    "📞 𝐂ᴏɴᴛᴀᴄᴛ 𝐎ᴡɴᴇʀ": _logic_contact_owner,
    "📊 𝐒ᴛᴀᴛɪꜱᴛɪᴄꜱ": _logic_statistics,
    "💳 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴꜱ": _logic_subscriptions_panel,
    "📢 𝐁ʀᴏᴀᴅᴄᴀꜱᴛ": _logic_broadcast_init,
    "🔒 𝐋ᴏᴄᴋ 𝐁ᴏᴛ": _logic_toggle_lock_bot,
    "🟢 𝐑ᴜɴɴɪɴɢ 𝐀ʟʟ 𝐂ᴏᴅᴇ": _logic_run_all_scripts,
    "👑 𝐀ᴅᴍɪɴ 𝐏ᴀɴᴇʟ": _logic_admin_panel,
    "⏳ 𝐏ᴇɴᴅɪɴɢ 𝐅ɪʟᴇꜱ": _logic_pending_files,
}

@bot.message_handler(func=lambda message: message.text in BUTTON_TEXT_TO_LOGIC)
def handle_button_text(message):
    logic_func = BUTTON_TEXT_TO_LOGIC.get(message.text)
    if logic_func: logic_func(message)

@bot.message_handler(commands=['updateschannel'])
def command_updates_channel(message): _logic_updates_channel(message)
@bot.message_handler(commands=['uploadfile'])
def command_upload_file(message): _logic_upload_file(message)
@bot.message_handler(commands=['checkfiles'])
def command_check_files(message): _logic_check_files(message)
@bot.message_handler(commands=['botspeed'])
def command_bot_speed(message): _logic_bot_speed(message)
@bot.message_handler(commands=['contactowner'])
def command_contact_owner(message): _logic_contact_owner(message)
@bot.message_handler(commands=['subscriptions'])
def command_subscriptions(message): _logic_subscriptions_panel(message)
@bot.message_handler(commands=['statistics'])
def command_statistics(message): _logic_statistics(message)
@bot.message_handler(commands=['broadcast'])
def command_broadcast(message): _logic_broadcast_init(message)
@bot.message_handler(commands=['lockbot'])
def command_lock_bot(message): _logic_toggle_lock_bot(message)
@bot.message_handler(commands=['adminpanel'])
def command_admin_panel(message): _logic_admin_panel(message)
@bot.message_handler(commands=['runningallcode'])
def command_run_all_code(message): _logic_run_all_scripts(message)
@bot.message_handler(commands=['pendingfiles'])
def command_pending_files(message): _logic_pending_files(message)


@bot.message_handler(commands=['ping'])
def ping(message):
    start_ping_time = time.time()
    msg = bot.reply_to(message, "𝐏ᴏɴɢ!")
    latency = round((time.time() - start_ping_time) * 1000, 2)
    bot.edit_message_text(f"𝐏ᴏɴɢ! 𝐋ᴀᴛᴇɴᴄʏ: {latency} ᴍꜱ", message.chat.id, msg.message_id)


# --- Document Handler ---
@bot.message_handler(content_types=['document'])
def handle_file_upload_doc(message):
    user_id = message.from_user.id
    chat_id = message.chat.id
    doc = message.document
    logger.info(f"Doc from {user_id}: {doc.file_name} ({doc.mime_type}), Size: {doc.file_size}")

    if bot_locked and user_id not in admin_ids:
        bot.reply_to(message, "⚠️ 𝐁ᴏᴛ ʟᴏᴄᴋᴇᴅ, ᴄᴀɴɴᴏᴛ ᴀᴄᴄᴇᴩᴛ ꜰɪʟᴇꜱ.")
        return

    file_limit = get_user_file_limit(user_id)
    current_files = get_user_file_count(user_id)
    if current_files >= file_limit:
        limit_str = str(file_limit) if file_limit != float('inf') else "Unlimited"
        bot.reply_to(message, f"⚠️ 𝐅ɪʟᴇ ʟɪᴍɪᴛ ({current_files}/{limit_str}) ʀᴇᴀᴄʜᴇᴅ.")
        return

    file_name = doc.file_name
    if not file_name: bot.reply_to(message, "⚠️ 𝐍ᴏ ꜰɪʟᴇ ɴᴀᴍᴇ."); return
    file_ext = os.path.splitext(file_name)[1].lower()
    if file_ext not in ['.py', '.js', '.zip']:
        bot.reply_to(message, "⚠️ 𝐎ɴʟʏ `.py`, `.js`, `.zip` ᴀʟʟᴏᴡᴇᴅ.")
        return
    max_file_size = 20 * 1024 * 1024
    if doc.file_size > max_file_size:
        bot.reply_to(message, f"⚠️ 𝐅ɪʟᴇ ᴛᴏᴏ ʟᴀʀɢᴇ (𝐌ᴀx: 20 𝐌𝐁)."); return

    try:
        try:
            bot.forward_message(OWNER_ID, chat_id, message.message_id)
            bot.send_message(OWNER_ID, f"⬆️ 𝐅ɪʟᴇ '{file_name}' ꜰʀᴏᴍ {message.from_user.first_name} (`{user_id}`)", parse_mode='Markdown')
        except Exception as e: logger.error(f"Failed to forward: {e}")

        download_wait_msg = bot.reply_to(message, f"⏳ 𝐃ᴏᴡɴʟᴏᴀᴅɪɴɢ `{file_name}`...")
        file_info_tg_doc = bot.get_file(doc.file_id)
        downloaded_file_content = bot.download_file(file_info_tg_doc.file_path)
        bot.edit_message_text(f"✅ 𝐃ᴏᴡɴʟᴏᴀᴅᴇᴅ `{file_name}`. 𝐏ʀᴏᴄᴇꜱꜱɪɴɢ...", chat_id, download_wait_msg.message_id)
        user_folder = get_user_folder(user_id)

        if file_ext == '.zip':
            handle_zip_file(downloaded_file_content, file_name, message)
        else:
            file_path = os.path.join(user_folder, file_name)
            with open(file_path, 'wb') as f: f.write(downloaded_file_content)

            if ADMIN_APPROVAL_REQUIRED and user_id not in admin_ids:
                save_pending_file(user_id, file_name, file_ext.replace('.', ''), message.message_id)
                bot.reply_to(
                    message,
                    f"⏳ 𝐅ɪʟᴇ `{file_name}` ᴜᴩʟᴏᴀᴅᴇᴅ!\n\n"
                    f"⚠️ **𝐀ᴅᴍɪɴ 𝐀ᴩᴩʀᴏᴠᴀʟ 𝐑ᴇQᴜɪʀᴇᴅ** ʙᴇꜰᴏʀᴇ ʜᴏꜱᴛɪɴɢ.\n"
                    f"𝐘ᴏᴜ'ʟʟ ʙᴇ ɴᴏᴛɪꜰɪᴇᴅ ᴏɴᴄᴇ ᴀᴅᴍɪɴ ᴀᴩᴩʀᴏᴠᴇꜱ/ʀᴇᴊᴇᴄᴛꜱ.",
                    parse_mode='Markdown'
                )
                notify_admins_for_approval(user_id, file_name, file_ext.replace('.', ''), message)
            else:
                if file_ext == '.js': handle_js_file(file_path, user_id, user_folder, file_name, message)
                elif file_ext == '.py': handle_py_file(file_path, user_id, user_folder, file_name, message)
    except telebot.apihelper.ApiTelegramException as e:
         logger.error(f"Telegram API Error: {e}", exc_info=True)
         if "file is too big" in str(e).lower():
              bot.reply_to(message, f"❌ 𝐅ɪʟᴇ ᴛᴏᴏ ʟᴀʀɢᴇ.")
         else: bot.reply_to(message, f"❌ 𝐀𝐏𝐈 𝐄ʀʀᴏʀ: {str(e)}")
    except Exception as e:
        logger.error(f"❌ Error: {e}", exc_info=True)
        bot.reply_to(message, f"❌ 𝐔ɴᴇxᴩᴇᴄᴛᴇᴅ 𝐄ʀʀᴏʀ: {str(e)}")


# --- Callback Query Handlers ---
@bot.callback_query_handler(func=lambda call: True)
def handle_callbacks(call):
    user_id = call.from_user.id
    data = call.data
    logger.info(f"Callback: User={user_id}, Data='{data}'")

    if bot_locked and user_id not in admin_ids and data not in ['back_to_main', 'speed', 'stats', 'updates_channel']:
        bot.answer_callback_query(call.id, "⚠️ 𝐁ᴏᴛ ʟᴏᴄᴋᴇᴅ ʙʏ ᴀᴅᴍɪɴ.", show_alert=True)
        return
    try:
        if data == 'upload': upload_callback(call)
        elif data == 'check_files': check_files_callback(call)
        elif data == 'updates_channel': updates_channel_callback(call)
        elif data.startswith('file_'): file_control_callback(call)
        elif data.startswith('start_'): start_bot_callback(call)
        elif data.startswith('stop_'): stop_bot_callback(call)
        elif data.startswith('restart_'): restart_bot_callback(call)
        elif data.startswith('delete_'): delete_bot_callback(call)
        elif data.startswith('logs_'): logs_bot_callback(call)
        elif data == 'speed': speed_callback(call)
        elif data == 'back_to_main': back_to_main_callback(call)
        elif data.startswith('confirm_broadcast_'): handle_confirm_broadcast(call)
        elif data == 'cancel_broadcast': handle_cancel_broadcast(call)
        elif data.startswith('approve_'): approve_file_callback(call)
        elif data.startswith('reject_'): reject_file_callback(call)
        elif data == 'pending_files': pending_files_callback(call)
        elif data == 'subscription': admin_required_callback(call, subscription_management_callback)
        elif data == 'stats': stats_callback(call)
        elif data == 'lock_bot': admin_required_callback(call, lock_bot_callback)
        elif data == 'unlock_bot': admin_required_callback(call, unlock_bot_callback)
        elif data == 'run_all_scripts': admin_required_callback(call, run_all_scripts_callback)
        elif data == 'broadcast': admin_required_callback(call, broadcast_init_callback)
        elif data == 'admin_panel': admin_required_callback(call, admin_panel_callback)
        elif data == 'add_admin': owner_required_callback(call, add_admin_init_callback)
        elif data == 'remove_admin': owner_required_callback(call, remove_admin_init_callback)
        elif data == 'list_admins': admin_required_callback(call, list_admins_callback)
        elif data == 'add_subscription': admin_required_callback(call, add_subscription_init_callback)
        elif data == 'remove_subscription': admin_required_callback(call, remove_subscription_init_callback)
        elif data == 'check_subscription': admin_required_callback(call, check_subscription_init_callback)
        else:
            bot.answer_callback_query(call.id, "Unknown action.")
    except Exception as e:
        logger.error(f"Error handling callback '{data}': {e}", exc_info=True)
        try: bot.answer_callback_query(call.id, "Error processing request.", show_alert=True)
        except Exception as e_ans: logger.error(f"Failed to answer callback: {e_ans}")

def admin_required_callback(call, func_to_run):
    if call.from_user.id not in admin_ids:
        bot.answer_callback_query(call.id, "⚠️ 𝐀ᴅᴍɪɴ ᴩᴇʀᴍɪꜱꜱɪᴏɴꜱ ʀᴇQᴜɪʀᴇᴅ.", show_alert=True)
        return
    func_to_run(call)

def owner_required_callback(call, func_to_run):
    if call.from_user.id != OWNER_ID:
        bot.answer_callback_query(call.id, "⚠️ 𝐎ᴡɴᴇʀ ᴩᴇʀᴍɪꜱꜱɪᴏɴꜱ ʀᴇQᴜɪʀᴇᴅ.", show_alert=True)
        return
    func_to_run(call)

def updates_channel_callback(call):
    user_id = call.from_user.id
    channel_username = UPDATE_CHANNEL.replace('@', '')
    channel_link = f"https://t.me/{channel_username}"
    text = (
        f"📢 **𝐎ꜰꜰɪᴄɪᴀʟ 𝐔ᴩᴅᴀᴛᴇꜱ 𝐂ʜᴀɴɴᴇʟ**\n\n"
        f"🔗 **𝐋ɪɴᴋ:** {channel_link}\n"
        f"📛 **𝐔ꜱᴇʀɴᴀᴍᴇ:** `@{channel_username}`\n\n"
        f"👆 𝐔ᴩᴀʀ ᴡᴀʟᴇ ʟɪɴᴋ ᴩᴇ ᴄʟɪᴄᴋ ᴋᴀʀᴏ ʏᴀ ᴄᴏᴩʏ ᴋᴀʀᴋᴇ 𝐓ᴇʟᴇɢʀᴀᴍ ᴍᴇ ᴏᴩᴇɴ ᴋᴀʀᴏ.\n\n"
        f"📌 𝐘ᴀʜᴀɴ ᴀᴀᴩᴋᴏ ᴍɪʟᴇɴɢᴇ:\n"
        f"• 𝐁ᴏᴛ ᴋᴇ ɴᴀʏᴇ ꜰᴇᴀᴛᴜʀᴇꜱ\n"
        f"• 𝐁ᴜɢ ꜰɪxᴇꜱ & ᴜᴩᴅᴀᴛᴇꜱ\n"
        f"• 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴ ᴩʟᴀɴꜱ\n"
        f"• 𝐂ᴏɴᴛᴀᴄᴛ ᴅᴇᴛᴀɪʟꜱ"
    )
    markup = types.InlineKeyboardMarkup()
    markup.add(types.InlineKeyboardButton('📢 𝐎ᴩᴇɴ 𝐂ʜᴀɴɴᴇʟ', url=channel_link))
    bot.answer_callback_query(call.id)
    try:
        bot.send_message(call.message.chat.id, text, reply_markup=markup, parse_mode='Markdown')
    except Exception as e:
        logger.error(f"Error sending updates channel: {e}")

def upload_callback(call):
    user_id = call.from_user.id
    file_limit = get_user_file_limit(user_id)
    current_files = get_user_file_count(user_id)
    if current_files >= file_limit:
        limit_str = str(file_limit) if file_limit != float('inf') else "Unlimited"
        bot.answer_callback_query(call.id, f"⚠️ 𝐅ɪʟᴇ ʟɪᴍɪᴛ ʀᴇᴀᴄʜᴇᴅ.", show_alert=True)
        return
    bot.answer_callback_query(call.id)
    bot.send_message(call.message.chat.id, "📤 𝐒ᴇɴᴅ ʏᴏᴜʀ 𝐏ʏᴛʜᴏɴ (`.py`), 𝐉𝐒 (`.js`), ᴏʀ 𝐙𝐈𝐏 (`.zip`) ꜰɪʟᴇ.\n\n⚠️ 𝐀ᴅᴍɪɴ ᴀᴩᴩʀᴏᴠᴀʟ ʀᴇQᴜɪʀᴇᴅ.")

def check_files_callback(call):
    user_id = call.from_user.id
    chat_id = call.message.chat.id
    user_files_list = user_files.get(user_id, [])
    pending_list = pending_approvals.get(user_id, [])

    if not user_files_list and not pending_list:
        bot.answer_callback_query(call.id, "⚠️ 𝐍ᴏ ꜰɪʟᴇꜱ ᴜᴩʟᴏᴀᴅᴇᴅ.", show_alert=True)
        try:
            markup = types.InlineKeyboardMarkup()
            markup.add(types.InlineKeyboardButton("🔙 𝐁ᴀᴄᴋ ᴛᴏ 𝐌ᴀɪɴ", callback_data='back_to_main'))
            bot.edit_message_text("📂 𝐘ᴏᴜʀ ꜰɪʟᴇꜱ:\n\n(𝐍ᴏ ꜰɪʟᴇꜱ ᴜᴩʟᴏᴀᴅᴇᴅ)", chat_id, call.message.message_id, reply_markup=markup)
        except Exception as e: logger.error(f"Error editing msg: {e}")
        return

    bot.answer_callback_query(call.id)
    text = "📂 **𝐘ᴏᴜʀ 𝐅ɪʟᴇꜱ:**\n\n"
    markup = types.InlineKeyboardMarkup(row_width=1)

    if pending_list:
        text += "⏳ **𝐏ᴇɴᴅɪɴɢ 𝐀ᴩᴩʀᴏᴠᴀʟ:**\n"
        for f in pending_list:
            text += f"  • `{f[0]}` ({f[1]}) - ⏳ 𝐖ᴀɪᴛɪɴɢ\n"
        text += "\n"

    if user_files_list:
        text += "✅ **𝐀ᴄᴛɪᴠᴇ 𝐅ɪʟᴇꜱ:**\n"
        for file_name, file_type in sorted(user_files_list):
            is_running = is_bot_running(user_id, file_name)
            status_icon = "🟢 𝐑ᴜɴɴɪɴɢ" if is_running else "🔴 𝐒ᴛᴏᴩᴩᴇᴅ"
            btn_text = f"{file_name} ({file_type}) - {status_icon}"
            markup.add(types.InlineKeyboardButton(btn_text, callback_data=f'file_{user_id}_{file_name}'))

    markup.add(types.InlineKeyboardButton("🔙 𝐁ᴀᴄᴋ ᴛᴏ 𝐌ᴀɪɴ", callback_data='back_to_main'))
    try:
        bot.edit_message_text(text, chat_id, call.message.message_id, reply_markup=markup, parse_mode='Markdown')
    except telebot.apihelper.ApiTelegramException as e:
         if "message is not modified" not in str(e): logger.error(f"Error editing msg: {e}")
    except Exception as e: logger.error(f"Error: {e}", exc_info=True)

def file_control_callback(call):
    try:
        _, script_owner_id_str, file_name = call.data.split('_', 2)
        script_owner_id = int(script_owner_id_str)
        requesting_user_id = call.from_user.id

        if not (requesting_user_id == script_owner_id or requesting_user_id in admin_ids):
            bot.answer_callback_query(call.id, "⚠️ 𝐘ᴏᴜ ᴄᴀɴ ᴏɴʟʏ ᴍᴀɴᴀɢᴇ ʏᴏᴜʀ ᴏᴡɴ ꜰɪʟᴇꜱ.", show_alert=True)
            check_files_callback(call)
            return

        user_files_list = user_files.get(script_owner_id, [])
        if not any(f[0] == file_name for f in user_files_list):
            bot.answer_callback_query(call.id, "⚠️ 𝐅ɪʟᴇ ɴᴏᴛ ꜰᴏᴜɴᴅ.", show_alert=True)
            check_files_callback(call)
            return

        bot.answer_callback_query(call.id)
        is_running = is_bot_running(script_owner_id, file_name)
        status_text = '🟢 𝐑ᴜɴɴɪɴɢ' if is_running else '🔴 𝐒ᴛᴏᴩᴩᴇᴅ'
        file_type = next((f[1] for f in user_files_list if f[0] == file_name), '?')
        try:
            bot.edit_message_text(
                f"⚙️ 𝐂ᴏɴᴛʀᴏʟꜱ ꜰᴏʀ: `{file_name}` ({file_type}) ᴏꜰ 𝐔ꜱᴇʀ `{script_owner_id}`\n𝐒ᴛᴀᴛᴜꜱ: {status_text}",
                call.message.chat.id, call.message.message_id,
                reply_markup=create_control_buttons(script_owner_id, file_name, is_running),
                parse_mode='Markdown'
            )
        except telebot.apihelper.ApiTelegramException as e:
             if "message is not modified" not in str(e): raise
    except (ValueError, IndexError) as ve:
        logger.error(f"Error parsing file control: {ve}")
        bot.answer_callback_query(call.id, "Error: Invalid action.", show_alert=True)
    except Exception as e:
        logger.error(f"Error in file_control: {e}", exc_info=True)
        bot.answer_callback_query(call.id, "Error occurred.", show_alert=True)

def start_bot_callback(call):
    try:
        _, script_owner_id_str, file_name = call.data.split('_', 2)
        script_owner_id = int(script_owner_id_str)
        requesting_user_id = call.from_user.id
        chat_id_for_reply = call.message.chat.id

        if not (requesting_user_id == script_owner_id or requesting_user_id in admin_ids):
            bot.answer_callback_query(call.id, "⚠️ 𝐏ᴇʀᴍɪꜱꜱɪᴏɴ ᴅᴇɴɪᴇᴅ.", show_alert=True); return

        user_files_list = user_files.get(script_owner_id, [])
        file_info = next((f for f in user_files_list if f[0] == file_name), None)
        if not file_info:
            bot.answer_callback_query(call.id, "⚠️ 𝐅ɪʟᴇ ɴᴏᴛ ꜰᴏᴜɴᴅ.", show_alert=True); check_files_callback(call); return

        file_type = file_info[1]
        user_folder = get_user_folder(script_owner_id)
        file_path = os.path.join(user_folder, file_name)

        if not os.path.exists(file_path):
            bot.answer_callback_query(call.id, f"⚠️ 𝐅ɪʟᴇ ᴍɪꜱꜱɪɴɢ! 𝐑ᴇ-ᴜᴩʟᴏᴀᴅ.", show_alert=True)
            remove_user_file_db(script_owner_id, file_name); check_files_callback(call); return

        if is_bot_running(script_owner_id, file_name):
            bot.answer_callback_query(call.id, f"⚠️ 𝐀ʟʀᴇᴀᴅʏ ʀᴜɴɴɪɴɢ.", show_alert=True)
            try: bot.edit_message_reply_markup(chat_id_for_reply, call.message.message_id, reply_markup=create_control_buttons(script_owner_id, file_name, True))
            except Exception as e: logger.error(f"Error: {e}")
            return

        bot.answer_callback_query(call.id, f"⏳ 𝐒ᴛᴀʀᴛɪɴɢ {file_name}...")

        if file_type == 'py':
            threading.Thread(target=run_script, args=(file_path, script_owner_id, user_folder, file_name, call.message)).start()
        elif file_type == 'js':
            threading.Thread(target=run_js_script, args=(file_path, script_owner_id, user_folder, file_name, call.message)).start()
        else:
             bot.send_message(chat_id_for_reply, f"❌ 𝐔ɴᴋɴᴏᴡɴ ᴛʏᴩᴇ."); return

        time.sleep(1.5)
        is_now_running = is_bot_running(script_owner_id, file_name)
        status_text = '🟢 𝐑ᴜɴɴɪɴɢ' if is_now_running else '🟡 𝐒ᴛᴀʀᴛɪɴɢ'
        try:
            bot.edit_message_text(
                f"⚙️ 𝐂ᴏɴᴛʀᴏʟꜱ ꜰᴏʀ: `{file_name}` ({file_type}) ᴏꜰ 𝐔ꜱᴇʀ `{script_owner_id}`\n𝐒ᴛᴀᴛᴜꜱ: {status_text}",
                chat_id_for_reply, call.message.message_id,
                reply_markup=create_control_buttons(script_owner_id, file_name, is_now_running), parse_mode='Markdown'
            )
        except telebot.apihelper.ApiTelegramException as e:
             if "message is not modified" not in str(e): raise
    except (ValueError, IndexError) as e:
        bot.answer_callback_query(call.id, "Error: Invalid.", show_alert=True)
    except Exception as e:
        logger.error(f"Error in start_bot: {e}", exc_info=True)
        bot.answer_callback_query(call.id, "Error starting.", show_alert=True)

def stop_bot_callback(call):
    try:
        _, script_owner_id_str, file_name = call.data.split('_', 2)
        script_owner_id = int(script_owner_id_str)
        requesting_user_id = call.from_user.id
        chat_id_for_reply = call.message.chat.id

        if not (requesting_user_id == script_owner_id or requesting_user_id in admin_ids):
            bot.answer_callback_query(call.id, "⚠️ 𝐏ᴇʀᴍɪꜱꜱɪᴏɴ ᴅᴇɴɪᴇᴅ.", show_alert=True); return

        user_files_list = user_files.get(script_owner_id, [])
        file_info = next((f for f in user_files_list if f[0] == file_name), None)
        if not file_info:
            bot.answer_callback_query(call.id, "⚠️ 𝐅ɪʟᴇ ɴᴏᴛ ꜰᴏᴜɴᴅ.", show_alert=True); check_files_callback(call); return

        file_type = file_info[1]
        script_key = f"{script_owner_id}_{file_name}"

        if not is_bot_running(script_owner_id, file_name):
            bot.answer_callback_query(call.id, f"⚠️ 𝐀ʟʀᴇᴀᴅʏ ꜱᴛᴏᴩᴩᴇᴅ.", show_alert=True)
            try:
                 bot.edit_message_text(
                     f"⚙️ 𝐂ᴏɴᴛʀᴏʟꜱ ꜰᴏʀ: `{file_name}` ({file_type}) ᴏꜰ 𝐔ꜱᴇʀ `{script_owner_id}`\n𝐒ᴛᴀᴛᴜꜱ: 🔴 𝐒ᴛᴏᴩᴩᴇᴅ",
                     chat_id_for_reply, call.message.message_id,
                     reply_markup=create_control_buttons(script_owner_id, file_name, False), parse_mode='Markdown')
            except Exception as e: logger.error(f"Error: {e}")
            return

        bot.answer_callback_query(call.id, f"⏳ 𝐒ᴛᴏᴩᴩɪɴɢ {file_name}...")
        process_info = bot_scripts.get(script_key)
        if process_info:
            kill_process_tree(process_info)
            if script_key in bot_scripts: del bot_scripts[script_key]

        try:
            bot.edit_message_text(
                f"⚙️ 𝐂ᴏɴᴛʀᴏʟꜱ ꜰᴏʀ: `{file_name}` ({file_type}) ᴏꜰ 𝐔ꜱᴇʀ `{script_owner_id}`\n𝐒ᴛᴀᴛᴜꜱ: 🔴 𝐒ᴛᴏᴩᴩᴇᴅ",
                chat_id_for_reply, call.message.message_id,
                reply_markup=create_control_buttons(script_owner_id, file_name, False), parse_mode='Markdown'
            )
        except telebot.apihelper.ApiTelegramException as e:
             if "message is not modified" not in str(e): raise
    except (ValueError, IndexError) as e:
        bot.answer_callback_query(call.id, "Error: Invalid.", show_alert=True)
    except Exception as e:
        logger.error(f"Error in stop_bot: {e}", exc_info=True)
        bot.answer_callback_query(call.id, "Error stopping.", show_alert=True)

def restart_bot_callback(call):
    try:
        _, script_owner_id_str, file_name = call.data.split('_', 2)
        script_owner_id = int(script_owner_id_str)
        requesting_user_id = call.from_user.id
        chat_id_for_reply = call.message.chat.id

        if not (requesting_user_id == script_owner_id or requesting_user_id in admin_ids):
            bot.answer_callback_query(call.id, "⚠️ 𝐏ᴇʀᴍɪꜱꜱɪᴏɴ ᴅᴇɴɪᴇᴅ.", show_alert=True); return

        user_files_list = user_files.get(script_owner_id, [])
        file_info = next((f for f in user_files_list if f[0] == file_name), None)
        if not file_info:
            bot.answer_callback_query(call.id, "⚠️ 𝐅ɪʟᴇ ɴᴏᴛ ꜰᴏᴜɴᴅ.", show_alert=True); check_files_callback(call); return

        file_type = file_info[1]; user_folder = get_user_folder(script_owner_id)
        file_path = os.path.join(user_folder, file_name); script_key = f"{script_owner_id}_{file_name}"

        if not os.path.exists(file_path):
            bot.answer_callback_query(call.id, f"⚠️ 𝐅ɪʟᴇ ᴍɪꜱꜱɪɴɢ!", show_alert=True)
            remove_user_file_db(script_owner_id, file_name)
            if script_key in bot_scripts: del bot_scripts[script_key]
            check_files_callback(call); return

        bot.answer_callback_query(call.id, f"⏳ 𝐑ᴇꜱᴛᴀʀᴛɪɴɢ {file_name}...")
        if is_bot_running(script_owner_id, file_name):
            process_info = bot_scripts.get(script_key)
            if process_info: kill_process_tree(process_info)
            if script_key in bot_scripts: del bot_scripts[script_key]
            time.sleep(1.5)

        if file_type == 'py':
            threading.Thread(target=run_script, args=(file_path, script_owner_id, user_folder, file_name, call.message)).start()
        elif file_type == 'js':
            threading.Thread(target=run_js_script, args=(file_path, script_owner_id, user_folder, file_name, call.message)).start()
        else:
             bot.send_message(chat_id_for_reply, f"❌ 𝐔ɴᴋɴᴏᴡɴ ᴛʏᴩᴇ."); return

        time.sleep(1.5)
        is_now_running = is_bot_running(script_owner_id, file_name)
        status_text = '🟢 𝐑ᴜɴɴɪɴɢ' if is_now_running else '🟡 𝐒ᴛᴀʀᴛɪɴɢ'
        try:
            bot.edit_message_text(
                f"⚙️ 𝐂ᴏɴᴛʀᴏʟꜱ ꜰᴏʀ: `{file_name}` ({file_type}) ᴏꜰ 𝐔ꜱᴇʀ `{script_owner_id}`\n𝐒ᴛᴀᴛᴜꜱ: {status_text}",
                chat_id_for_reply, call.message.message_id,
                reply_markup=create_control_buttons(script_owner_id, file_name, is_now_running), parse_mode='Markdown'
            )
        except telebot.apihelper.ApiTelegramException as e:
             if "message is not modified" not in str(e): raise
    except (ValueError, IndexError) as e:
        bot.answer_callback_query(call.id, "Error: Invalid.", show_alert=True)
    except Exception as e:
        logger.error(f"Error in restart: {e}", exc_info=True)
        bot.answer_callback_query(call.id, "Error restarting.", show_alert=True)


def delete_bot_callback(call):
    try:
        _, script_owner_id_str, file_name = call.data.split('_', 2)
        script_owner_id = int(script_owner_id_str)
        requesting_user_id = call.from_user.id
        chat_id_for_reply = call.message.chat.id

        if not (requesting_user_id == script_owner_id or requesting_user_id in admin_ids):
            bot.answer_callback_query(call.id, "⚠️ 𝐏ᴇʀᴍɪꜱꜱɪᴏɴ ᴅᴇɴɪᴇᴅ.", show_alert=True); return

        user_files_list = user_files.get(script_owner_id, [])
        if not any(f[0] == file_name for f in user_files_list):
            bot.answer_callback_query(call.id, "⚠️ 𝐅ɪʟᴇ ɴᴏᴛ ꜰᴏᴜɴᴅ.", show_alert=True); check_files_callback(call); return

        bot.answer_callback_query(call.id, f"🗑️ 𝐃ᴇʟᴇᴛɪɴɢ {file_name}...")
        script_key = f"{script_owner_id}_{file_name}"
        if is_bot_running(script_owner_id, file_name):
            process_info = bot_scripts.get(script_key)
            if process_info: kill_process_tree(process_info)
            if script_key in bot_scripts: del bot_scripts[script_key]
            time.sleep(0.5)

        user_folder = get_user_folder(script_owner_id)
        file_path = os.path.join(user_folder, file_name)
        log_path = os.path.join(user_folder, f"{os.path.splitext(file_name)[0]}.log")
        deleted_disk = []
        if os.path.exists(file_path):
            try: os.remove(file_path); deleted_disk.append(file_name)
            except OSError as e: logger.error(f"Error deleting: {e}")
        if os.path.exists(log_path):
            try: os.remove(log_path); deleted_disk.append(os.path.basename(log_path))
            except OSError as e: logger.error(f"Error deleting log: {e}")

        remove_user_file_db(script_owner_id, file_name)
        try:
            bot.edit_message_text(
                f"🗑️ `{file_name}` 𝐃ᴇʟᴇᴛᴇᴅ!",
                chat_id_for_reply, call.message.message_id, reply_markup=None, parse_mode='Markdown'
            )
        except Exception as e:
            logger.error(f"Error editing msg: {e}")
    except (ValueError, IndexError) as e:
        bot.answer_callback_query(call.id, "Error: Invalid.", show_alert=True)
    except Exception as e:
        logger.error(f"Error in delete: {e}", exc_info=True)
        bot.answer_callback_query(call.id, "Error deleting.", show_alert=True)

def logs_bot_callback(call):
    try:
        _, script_owner_id_str, file_name = call.data.split('_', 2)
        script_owner_id = int(script_owner_id_str)
        requesting_user_id = call.from_user.id
        chat_id_for_reply = call.message.chat.id

        if not (requesting_user_id == script_owner_id or requesting_user_id in admin_ids):
            bot.answer_callback_query(call.id, "⚠️ 𝐏ᴇʀᴍɪꜱꜱɪᴏɴ ᴅᴇɴɪᴇᴅ.", show_alert=True); return

        user_folder = get_user_folder(script_owner_id)
        log_path = os.path.join(user_folder, f"{os.path.splitext(file_name)[0]}.log")
        if not os.path.exists(log_path):
            bot.answer_callback_query(call.id, f"⚠️ 𝐍ᴏ ʟᴏɢꜱ.", show_alert=True); return

        bot.answer_callback_query(call.id)
        try:
            log_content = ""; file_size = os.path.getsize(log_path)
            max_log_kb = 100; max_tg_msg = 4096
            if file_size == 0: log_content = "(Log empty)"
            elif file_size > max_log_kb * 1024:
                 with open(log_path, 'rb') as f: f.seek(-max_log_kb * 1024, os.SEEK_END); log_bytes = f.read()
                 log_content = log_bytes.decode('utf-8', errors='ignore')
                 log_content = f"(Last {max_log_kb} KB)\n...\n" + log_content
            else:
                 with open(log_path, 'r', encoding='utf-8', errors='ignore') as f: log_content = f.read()

            if len(log_content) > max_tg_msg:
                log_content = log_content[-max_tg_msg:]
                first_nl = log_content.find('\n')
                if first_nl != -1: log_content = "...\n" + log_content[first_nl+1:]
                else: log_content = "...\n" + log_content
            if not log_content.strip(): log_content = "(No visible content)"

            bot.send_message(chat_id_for_reply, f"📜 𝐋ᴏɢꜱ ꜰᴏʀ `{file_name}`:\n```\n{log_content}\n```", parse_mode='Markdown')
        except Exception as e:
            logger.error(f"Error reading log: {e}", exc_info=True)
            bot.send_message(chat_id_for_reply, f"❌ 𝐄ʀʀᴏʀ ʀᴇᴀᴅɪɴɢ ʟᴏɢ.")
    except (ValueError, IndexError) as e:
        bot.answer_callback_query(call.id, "Error: Invalid.", show_alert=True)
    except Exception as e:
        logger.error(f"Error in logs: {e}", exc_info=True)
        bot.answer_callback_query(call.id, "Error.", show_alert=True)

def speed_callback(call):
    user_id = call.from_user.id
    chat_id = call.message.chat.id
    start_cb_ping_time = time.time()
    try:
        bot.edit_message_text("🏃 𝐓ᴇꜱᴛɪɴɢ ꜱᴩᴇᴇᴅ...", chat_id, call.message.message_id)
        bot.send_chat_action(chat_id, 'typing')
        response_time = round((time.time() - start_cb_ping_time) * 1000, 2)
        status = "🔓 𝐔ɴʟᴏᴄᴋᴇᴅ" if not bot_locked else "🔒 𝐋ᴏᴄᴋᴇᴅ"
        if user_id == OWNER_ID: user_level = "👑 𝐎ᴡɴᴇʀ"
        elif user_id in admin_ids: user_level = "🛡️ 𝐀ᴅᴍɪɴ"
        elif user_id in user_subscriptions and user_subscriptions[user_id].get('expiry', datetime.min) > datetime.now(): user_level = "⭐ 𝐏ʀᴇᴍɪᴜᴍ"
        else: user_level = "🆓 𝐅ʀᴇᴇ 𝐔ꜱᴇʀ"
        speed_msg = (f"⚡ 𝐁ᴏᴛ 𝐒ᴩᴇᴇᴅ & 𝐒ᴛᴀᴛᴜꜱ:\n\n⏱️ 𝐀𝐏𝐈 𝐑ᴇꜱᴩᴏɴꜱᴇ 𝐓ɪᴍᴇ: {response_time} ᴍꜱ\n"
                     f"🚦 𝐁ᴏᴛ 𝐒ᴛᴀᴛᴜꜱ: {status}\n"
                     f"👤 𝐘ᴏᴜʀ 𝐋ᴇᴠᴇʟ: {user_level}")
        bot.answer_callback_query(call.id)
        bot.edit_message_text(speed_msg, chat_id, call.message.message_id, reply_markup=create_main_menu_inline(user_id))
    except Exception as e:
         logger.error(f"Error in speed: {e}", exc_info=True)
         bot.answer_callback_query(call.id, "Error.", show_alert=True)

def back_to_main_callback(call):
    user_id = call.from_user.id
    chat_id = call.message.chat.id
    file_limit = get_user_file_limit(user_id)
    current_files = get_user_file_count(user_id)
    limit_str = str(file_limit) if file_limit != float('inf') else "Unlimited"
    expiry_info = ""
    if user_id == OWNER_ID: user_status = "👑 𝐎ᴡɴᴇʀ"
    elif user_id in admin_ids: user_status = "🛡️ 𝐀ᴅᴍɪɴ"
    elif user_id in user_subscriptions:
        expiry_date = user_subscriptions[user_id].get('expiry')
        if expiry_date and expiry_date > datetime.now():
            user_status = "⭐ 𝐏ʀᴇᴍɪᴜᴍ"; days_left = (expiry_date - datetime.now()).days
            expiry_info = f"\n⏳ 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴ ᴇxᴩɪʀᴇꜱ ɪɴ: {days_left} ᴅᴀʏꜱ"
        else: user_status = "🆓 𝐅ʀᴇᴇ 𝐔ꜱᴇʀ (𝐄xᴩɪʀᴇᴅ)"
    else: user_status = "🆓 𝐅ʀᴇᴇ 𝐔ꜱᴇʀ"
    main_menu_text = (f"〽️ 𝐖ᴇʟᴄᴏᴍᴇ ʙᴀᴄᴋ, {call.from_user.first_name}!\n\n🆔 𝐈𝐃: `{user_id}`\n"
                      f"🔰 𝐒ᴛᴀᴛᴜꜱ: {user_status}{expiry_info}\n📁 𝐅ɪʟᴇꜱ: {current_files} / {limit_str}\n\n"
                      f"👇 𝐔ꜱᴇ ʙᴜᴛᴛᴏɴꜱ ᴏʀ ᴛʏᴩᴇ ᴄᴏᴍᴍᴀɴᴅꜱ.")
    try:
        bot.answer_callback_query(call.id)
        bot.edit_message_text(main_menu_text, chat_id, call.message.message_id,
                              reply_markup=create_main_menu_inline(user_id), parse_mode='Markdown')
    except telebot.apihelper.ApiTelegramException as e:
         if "message is not modified" not in str(e): logger.error(f"API error: {e}")
    except Exception as e: logger.error(f"Error: {e}", exc_info=True)

# --- Admin Callback Implementations ---
def subscription_management_callback(call):
    bot.answer_callback_query(call.id)
    try:
        bot.edit_message_text("💳 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴ 𝐌ᴀɴᴀɢᴇᴍᴇɴᴛ\n𝐒ᴇʟᴇᴄᴛ ᴀᴄᴛɪᴏɴ:",
                              call.message.chat.id, call.message.message_id, reply_markup=create_subscription_menu())
    except Exception as e: logger.error(f"Error: {e}")

def stats_callback(call):
    bot.answer_callback_query(call.id)
    _logic_statistics(call.message)
    try:
        bot.edit_message_reply_markup(call.message.chat.id, call.message.message_id,
                                      reply_markup=create_main_menu_inline(call.from_user.id))
    except Exception as e:
        logger.error(f"Error: {e}")


def lock_bot_callback(call):
    global bot_locked; bot_locked = True
    logger.warning(f"Bot locked by {call.from_user.id}")
    bot.answer_callback_query(call.id, "🔒 𝐁ᴏᴛ ʟᴏᴄᴋᴇᴅ.")
    try: bot.edit_message_reply_markup(call.message.chat.id, call.message.message_id, reply_markup=create_main_menu_inline(call.from_user.id))
    except Exception as e: logger.error(f"Error: {e}")

def unlock_bot_callback(call):
    global bot_locked; bot_locked = False
    logger.warning(f"Bot unlocked by {call.from_user.id}")
    bot.answer_callback_query(call.id, "🔓 𝐁ᴏᴛ ᴜɴʟᴏᴄᴋᴇᴅ.")
    try: bot.edit_message_reply_markup(call.message.chat.id, call.message.message_id, reply_markup=create_main_menu_inline(call.from_user.id))
    except Exception as e: logger.error(f"Error: {e}")

def run_all_scripts_callback(call):
    _logic_run_all_scripts(call)

def broadcast_init_callback(call):
    bot.answer_callback_query(call.id)
    msg = bot.send_message(call.message.chat.id, "📢 𝐒ᴇɴᴅ ᴍᴇꜱꜱᴀɢᴇ ᴛᴏ ʙʀᴏᴀᴅᴄᴀꜱᴛ.\n/cancel ᴛᴏ ᴀʙᴏʀᴛ.")
    bot.register_next_step_handler(msg, process_broadcast_message)

def process_broadcast_message(message):
    user_id = message.from_user.id
    if user_id not in admin_ids: bot.reply_to(message, "⚠️ 𝐍ᴏᴛ ᴀᴜᴛʜᴏʀɪᴢᴇᴅ."); return
    if message.text and message.text.lower() == '/cancel': bot.reply_to(message, "𝐂ᴀɴᴄᴇʟʟᴇᴅ."); return

    broadcast_content = message.text
    if not broadcast_content and not (message.photo or message.video or message.document or message.sticker or message.voice or message.audio):
         bot.reply_to(message, "⚠️ 𝐄ᴍᴩᴛʏ ᴍᴇꜱꜱᴀɢᴇ. /cancel ᴛᴏ ᴀʙᴏʀᴛ.")
         msg = bot.send_message(message.chat.id, "📢 𝐒ᴇɴᴅ ʙʀᴏᴀᴅᴄᴀꜱᴛ ᴍᴇꜱꜱᴀɢᴇ ᴏʀ /cancel.")
         bot.register_next_step_handler(msg, process_broadcast_message)
         return

    target_count = len(active_users)
    markup = types.InlineKeyboardMarkup()
    markup.row(types.InlineKeyboardButton("✅ 𝐂ᴏɴꜰɪʀᴍ & 𝐒ᴇɴᴅ", callback_data=f"confirm_broadcast_{message.message_id}"),
               types.InlineKeyboardButton("❌ 𝐂ᴀɴᴄᴇʟ", callback_data="cancel_broadcast"))

    preview_text = broadcast_content[:1000].strip() if broadcast_content else "(Media message)"
    bot.reply_to(message, f"⚠️ 𝐂ᴏɴꜰɪʀᴍ 𝐁ʀᴏᴀᴅᴄᴀꜱᴛ:\n\n```\n{preview_text}\n```\n"
                          f"𝐓ᴏ **{target_count}** ᴜꜱᴇʀꜱ. 𝐒ᴜʀᴇ?", reply_markup=markup, parse_mode='Markdown')

def handle_confirm_broadcast(call):
    user_id = call.from_user.id
    chat_id = call.message.chat.id
    if user_id not in admin_ids: bot.answer_callback_query(call.id, "⚠️ 𝐀ᴅᴍɪɴ ᴏɴʟʏ.", show_alert=True); return
    try:
        original_message = call.message.reply_to_message
        if not original_message: raise ValueError("Could not retrieve original message.")

        broadcast_text = None
        broadcast_photo_id = None
        broadcast_video_id = None

        if original_message.text:
            broadcast_text = original_message.text
        elif original_message.photo:
            broadcast_photo_id = original_message.photo[-1].file_id
        elif original_message.video:
            broadcast_video_id = original_message.video.file_id
        else:
            raise ValueError("No text or supported media.")

        bot.answer_callback_query(call.id, "🚀 𝐒ᴛᴀʀᴛɪɴɢ ʙʀᴏᴀᴅᴄᴀꜱᴛ...")
        bot.edit_message_text(f"📢 𝐁ʀᴏᴀᴅᴄᴀꜱᴛɪɴɢ ᴛᴏ {len(active_users)} ᴜꜱᴇʀꜱ...",
                              chat_id, call.message.message_id, reply_markup=None)
        thread = threading.Thread(target=execute_broadcast, args=(
            broadcast_text, broadcast_photo_id, broadcast_video_id,
            original_message.caption if (broadcast_photo_id or broadcast_video_id) else None,
            chat_id))
        thread.start()
    except ValueError as ve:
        logger.error(f"Error: {ve}")
        bot.edit_message_text(f"❌ 𝐄ʀʀᴏʀ: {ve}", chat_id, call.message.message_id, reply_markup=None)
    except Exception as e:
        logger.error(f"Error: {e}", exc_info=True)
        bot.edit_message_text("❌ 𝐔ɴᴇxᴩᴇᴄᴛᴇᴅ ᴇʀʀᴏʀ.", chat_id, call.message.message_id, reply_markup=None)

def handle_cancel_broadcast(call):
    bot.answer_callback_query(call.id, "𝐂ᴀɴᴄᴇʟʟᴇᴅ.")
    bot.delete_message(call.message.chat.id, call.message.message_id)
    if call.message.reply_to_message:
        try: bot.delete_message(call.message.chat.id, call.message.reply_to_message.message_id)
        except: pass


def execute_broadcast(broadcast_text, photo_id, video_id, caption, admin_chat_id):
    sent_count = 0; failed_count = 0; blocked_count = 0
    start_exec_time = time.time()
    users_to_broadcast = list(active_users); total_users = len(users_to_broadcast)
    batch_size = 25; delay_batches = 1.5

    for i, user_id_bc in enumerate(users_to_broadcast):
        try:
            if broadcast_text:
                bot.send_message(user_id_bc, broadcast_text, parse_mode='Markdown')
            elif photo_id:
                bot.send_photo(user_id_bc, photo_id, caption=caption, parse_mode='Markdown' if caption else None)
            elif video_id:
                bot.send_video(user_id_bc, video_id, caption=caption, parse_mode='Markdown' if caption else None)
            sent_count += 1
        except telebot.apihelper.ApiTelegramException as e:
            err_desc = str(e).lower()
            if any(s in err_desc for s in ["bot was blocked", "user is deactivated", "chat not found", "kicked from", "restricted"]):
                blocked_count += 1
            elif "flood control" in err_desc or "too many requests" in err_desc:
                retry_after = 5; match = re.search(r"retry after (\d+)", err_desc)
                if match: retry_after = int(match.group(1)) + 1
                time.sleep(retry_after)
                try:
                    if broadcast_text: bot.send_message(user_id_bc, broadcast_text, parse_mode='Markdown')
                    elif photo_id: bot.send_photo(user_id_bc, photo_id, caption=caption, parse_mode='Markdown' if caption else None)
                    elif video_id: bot.send_video(user_id_bc, video_id, caption=caption, parse_mode='Markdown' if caption else None)
                    sent_count += 1
                except Exception as e_retry: failed_count +=1
            else: failed_count += 1
        except Exception as e: failed_count += 1

        if (i + 1) % batch_size == 0 and i < total_users - 1:
            time.sleep(delay_batches)
        elif i % 5 == 0: time.sleep(0.2)

    duration = round(time.time() - start_exec_time, 2)
    result_msg = (f"📢 𝐁ʀᴏᴀᴅᴄᴀꜱᴛ 𝐂ᴏᴍᴩʟᴇᴛᴇ!\n\n✅ 𝐒ᴇɴᴛ: {sent_count}\n❌ 𝐅ᴀɪʟᴇᴅ: {failed_count}\n"
                  f"🚫 𝐁ʟᴏᴄᴋᴇᴅ: {blocked_count}\n👥 𝐓ᴀʀɢᴇᴛꜱ: {total_users}\n⏱️ 𝐃ᴜʀᴀᴛɪᴏɴ: {duration}s")
    logger.info(result_msg)
    try: bot.send_message(admin_chat_id, result_msg)
    except Exception as e: logger.error(f"Error: {e}")

def admin_panel_callback(call):
    bot.answer_callback_query(call.id)
    try:
        bot.edit_message_text("👑 𝐀ᴅᴍɪɴ 𝐏ᴀɴᴇʟ\n𝐌ᴀɴᴀɢᴇ ᴀᴅᴍɪɴꜱ.",
                              call.message.chat.id, call.message.message_id, reply_markup=create_admin_panel())
    except Exception as e: logger.error(f"Error: {e}")

def add_admin_init_callback(call):
    bot.answer_callback_query(call.id)
    msg = bot.send_message(call.message.chat.id, "👑 𝐄ɴᴛᴇʀ 𝐔ꜱᴇʀ 𝐈𝐃 ᴛᴏ ᴩʀᴏᴍᴏᴛᴇ.\n/cancel ᴛᴏ ᴀʙᴏʀᴛ.")
    bot.register_next_step_handler(msg, process_add_admin_id)

def process_add_admin_id(message):
    owner_id_check = message.from_user.id
    if owner_id_check != OWNER_ID: bot.reply_to(message, "⚠️ 𝐎ᴡɴᴇʀ ᴏɴʟʏ."); return
    if message.text.lower() == '/cancel': bot.reply_to(message, "𝐂ᴀɴᴄᴇʟʟᴇᴅ."); return
    try:
        new_admin_id = int(message.text.strip())
        if new_admin_id <= 0: raise ValueError("ID must be positive")
        if new_admin_id == OWNER_ID: bot.reply_to(message, "⚠️ 𝐀ʟʀᴇᴀᴅʏ 𝐎ᴡɴᴇʀ."); return
        if new_admin_id in admin_ids: bot.reply_to(message, f"⚠️ 𝐀ʟʀᴇᴀᴅʏ 𝐀ᴅᴍɪɴ."); return
        add_admin_db(new_admin_id)
        bot.reply_to(message, f"✅ `{new_admin_id}` ᴩʀᴏᴍᴏᴛᴇᴅ ᴛᴏ 𝐀ᴅᴍɪɴ.")
        try: bot.send_message(new_admin_id, "🎉 𝐘ᴏᴜ ᴀʀᴇ ɴᴏᴡ 𝐀ᴅᴍɪɴ.")
        except Exception as e: logger.error(f"Error: {e}")
    except ValueError:
        bot.reply_to(message, "⚠️ 𝐈ɴᴠᴀʟɪᴅ 𝐈𝐃. /cancel ᴛᴏ ᴀʙᴏʀᴛ.")
        msg = bot.send_message(message.chat.id, "👑 𝐄ɴᴛᴇʀ 𝐔ꜱᴇʀ 𝐈𝐃.")
        bot.register_next_step_handler(msg, process_add_admin_id)
    except Exception as e: logger.error(f"Error: {e}", exc_info=True); bot.reply_to(message, "Error.")

def remove_admin_init_callback(call):
    bot.answer_callback_query(call.id)
    msg = bot.send_message(call.message.chat.id, "👑 𝐄ɴᴛᴇʀ 𝐀ᴅᴍɪɴ 𝐈𝐃 ᴛᴏ ʀᴇᴍᴏᴠᴇ.\n/cancel ᴛᴏ ᴀʙᴏʀᴛ.")
    bot.register_next_step_handler(msg, process_remove_admin_id)

def process_remove_admin_id(message):
    owner_id_check = message.from_user.id
    if owner_id_check != OWNER_ID: bot.reply_to(message, "⚠️ 𝐎ᴡɴᴇʀ ᴏɴʟʏ."); return
    if message.text.lower() == '/cancel': bot.reply_to(message, "𝐂ᴀɴᴄᴇʟʟᴇᴅ."); return
    try:
        admin_id_remove = int(message.text.strip())
        if admin_id_remove <= 0: raise ValueError("ID must be positive")
        if admin_id_remove == OWNER_ID: bot.reply_to(message, "⚠️ 𝐂ᴀɴɴᴏᴛ ʀᴇᴍᴏᴠᴇ 𝐎ᴡɴᴇʀ."); return
        if admin_id_remove not in admin_ids: bot.reply_to(message, f"⚠️ 𝐍ᴏᴛ 𝐀ᴅᴍɪɴ."); return
        if remove_admin_db(admin_id_remove):
            bot.reply_to(message, f"✅ 𝐀ᴅᴍɪɴ `{admin_id_remove}` ʀᴇᴍᴏᴠᴇᴅ.")
            try: bot.send_message(admin_id_remove, "ℹ️ 𝐍ᴏ ʟᴏɴɢᴇʀ 𝐀ᴅᴍɪɴ.")
            except Exception as e: logger.error(f"Error: {e}")
        else: bot.reply_to(message, f"❌ 𝐅ᴀɪʟᴇᴅ ᴛᴏ ʀᴇᴍᴏᴠᴇ.")
    except ValueError:
        bot.reply_to(message, "⚠️ 𝐈ɴᴠᴀʟɪᴅ 𝐈𝐃. /cancel.")
        msg = bot.send_message(message.chat.id, "👑 𝐄ɴᴛᴇʀ 𝐀ᴅᴍɪɴ 𝐈𝐃.")
        bot.register_next_step_handler(msg, process_remove_admin_id)
    except Exception as e: logger.error(f"Error: {e}", exc_info=True); bot.reply_to(message, "Error.")

def list_admins_callback(call):
    bot.answer_callback_query(call.id)
    try:
        admin_list_str = "\n".join(f"- `{aid}` {'(Owner)' if aid == OWNER_ID else ''}" for aid in sorted(list(admin_ids)))
        if not admin_list_str: admin_list_str = "(No admins configured!)"
        bot.edit_message_text(f"👑 𝐂ᴜʀʀᴇɴᴛ 𝐀ᴅᴍɪɴꜱ:\n\n{admin_list_str}", call.message.chat.id,
                              call.message.message_id, reply_markup=create_admin_panel(), parse_mode='Markdown')
    except Exception as e: logger.error(f"Error: {e}")

def add_subscription_init_callback(call):
    bot.answer_callback_query(call.id)
    msg = bot.send_message(call.message.chat.id, "💳 𝐄ɴᴛᴇʀ 𝐔ꜱᴇʀ 𝐈𝐃 & ᴅᴀʏꜱ (ᴇ.ɢ., `12345678 30`).\n/cancel ᴛᴏ ᴀʙᴏʀᴛ.")
    bot.register_next_step_handler(msg, process_add_subscription_details)

def process_add_subscription_details(message):
    admin_id_check = message.from_user.id
    if admin_id_check not in admin_ids: bot.reply_to(message, "⚠️ 𝐍ᴏᴛ ᴀᴜᴛʜᴏʀɪᴢᴇᴅ."); return
    if message.text.lower() == '/cancel': bot.reply_to(message, "𝐂ᴀɴᴄᴇʟʟᴇᴅ."); return
    try:
        parts = message.text.split()
        if len(parts) != 2: raise ValueError("Incorrect format")
        sub_user_id = int(parts[0].strip()); days = int(parts[1].strip())
        if sub_user_id <= 0 or days <= 0: raise ValueError("Must be positive")

        current_expiry = user_subscriptions.get(sub_user_id, {}).get('expiry')
        start_date_new_sub = datetime.now()
        if current_expiry and current_expiry > start_date_new_sub: start_date_new_sub = current_expiry
        new_expiry = start_date_new_sub + timedelta(days=days)
        save_subscription(sub_user_id, new_expiry)

        bot.reply_to(message, f"✅ 𝐒ᴜʙ ꜰᴏʀ `{sub_user_id}` ʙʏ {days} ᴅᴀʏꜱ.\n𝐄xᴩɪʀʏ: {new_expiry:%Y-%m-%d}")
        try: bot.send_message(sub_user_id, f"🎉 𝐒ᴜʙ ᴀᴄᴛɪᴠᴀᴛᴇᴅ ʙʏ {days} ᴅᴀʏꜱ! 𝐄xᴩɪʀᴇꜱ: {new_expiry:%Y-%m-%d}.")
        except Exception as e: logger.error(f"Error: {e}")
    except ValueError as e:
        bot.reply_to(message, f"⚠️ 𝐈ɴᴠᴀʟɪᴅ: {e}. 𝐅ᴏʀᴍᴀᴛ: `ID days` ᴏʀ /cancel.")
        msg = bot.send_message(message.chat.id, "💳 𝐄ɴᴛᴇʀ 𝐔ꜱᴇʀ 𝐈𝐃 & ᴅᴀʏꜱ, ᴏʀ /cancel.")
        bot.register_next_step_handler(msg, process_add_subscription_details)
    except Exception as e: logger.error(f"Error: {e}", exc_info=True); bot.reply_to(message, "Error.")

def remove_subscription_init_callback(call):
    bot.answer_callback_query(call.id)
    msg = bot.send_message(call.message.chat.id, "💳 𝐄ɴᴛᴇʀ 𝐔ꜱᴇʀ 𝐈𝐃 ᴛᴏ ʀᴇᴍᴏᴠᴇ ꜱᴜʙ.\n/cancel ᴛᴏ ᴀʙᴏʀᴛ.")
    bot.register_next_step_handler(msg, process_remove_subscription_id)

def process_remove_subscription_id(message):
    admin_id_check = message.from_user.id
    if admin_id_check not in admin_ids: bot.reply_to(message, "⚠️ 𝐍ᴏᴛ ᴀᴜᴛʜᴏʀɪᴢᴇᴅ."); return
    if message.text.lower() == '/cancel': bot.reply_to(message, "𝐂ᴀɴᴄᴇʟʟᴇᴅ."); return
    try:
        sub_user_id_remove = int(message.text.strip())
        if sub_user_id_remove <= 0: raise ValueError("Must be positive")
        if sub_user_id_remove not in user_subscriptions:
            bot.reply_to(message, f"⚠️ 𝐍ᴏ ᴀᴄᴛɪᴠᴇ ꜱᴜʙ."); return
        remove_subscription_db(sub_user_id_remove)
        bot.reply_to(message, f"✅ 𝐒ᴜʙ ʀᴇᴍᴏᴠᴇᴅ.")
        try: bot.send_message(sub_user_id_remove, "ℹ️ 𝐒ᴜʙꜱᴄʀɪᴩᴛɪᴏɴ ʀᴇᴍᴏᴠᴇᴅ.")
        except Exception as e: logger.error(f"Error: {e}")
    except ValueError:
        bot.reply_to(message, "⚠️ 𝐈ɴᴠᴀʟɪᴅ 𝐈𝐃. /cancel.")
        msg = bot.send_message(message.chat.id, "💳 𝐄ɴᴛᴇʀ 𝐔ꜱᴇʀ 𝐈𝐃.")
        bot.register_next_step_handler(msg, process_remove_subscription_id)
    except Exception as e: logger.error(f"Error: {e}", exc_info=True); bot.reply_to(message, "Error.")

def check_subscription_init_callback(call):
    bot.answer_callback_query(call.id)
    msg = bot.send_message(call.message.chat.id, "💳 𝐄ɴᴛᴇʀ 𝐔ꜱᴇʀ 𝐈𝐃 ᴛᴏ ᴄʜᴇᴄᴋ ꜱᴜʙ.\n/cancel ᴛᴏ ᴀʙᴏʀᴛ.")
    bot.register_next_step_handler(msg, process_check_subscription_id)

def process_check_subscription_id(message):
    admin_id_check = message.from_user.id
    if admin_id_check not in admin_ids: bot.reply_to(message, "⚠️ 𝐍ᴏᴛ ᴀᴜᴛʜᴏʀɪᴢᴇᴅ."); return
    if message.text.lower() == '/cancel': bot.reply_to(message, "𝐂ᴀɴᴄᴇʟʟᴇᴅ."); return
    try:
        sub_user_id_check = int(message.text.strip())
        if sub_user_id_check <= 0: raise ValueError("Must be positive")
        if sub_user_id_check in user_subscriptions:
            expiry_dt = user_subscriptions[sub_user_id_check].get('expiry')
            if expiry_dt:
                if expiry_dt > datetime.now():
                    days_left = (expiry_dt - datetime.now()).days
                    bot.reply_to(message, f"✅ 𝐀ᴄᴛɪᴠᴇ ꜱᴜʙ.\n𝐄xᴩɪʀᴇꜱ: {expiry_dt:%Y-%m-%d %H:%M:%S} ({days_left} ᴅᴀʏꜱ ʟᴇꜰᴛ).")
                else:
                    bot.reply_to(message, f"⚠️ 𝐄xᴩɪʀᴇᴅ ꜱᴜʙ (𝐎ɴ: {expiry_dt:%Y-%m-%d %H:%M:%S}).")
                    remove_subscription_db(sub_user_id_check)
            else: bot.reply_to(message, f"⚠️ 𝐄xᴩɪʀʏ ᴍɪꜱꜱɪɴɢ.")
        else: bot.reply_to(message, f"ℹ️ 𝐍ᴏ ꜱᴜʙ ʀᴇᴄᴏʀᴅ.")
    except ValueError:
        bot.reply_to(message, "⚠️ 𝐈ɴᴠᴀʟɪᴅ 𝐈𝐃. /cancel.")
        msg = bot.send_message(message.chat.id, "💳 𝐄ɴᴛᴇʀ 𝐔ꜱᴇʀ 𝐈𝐃.")
        bot.register_next_step_handler(msg, process_check_subscription_id)
    except Exception as e: logger.error(f"Error: {e}", exc_info=True); bot.reply_to(message, "Error.")

def approve_file_callback(call):
    if call.from_user.id not in admin_ids:
        bot.answer_callback_query(call.id, "⚠️ 𝐀ᴅᴍɪɴ ᴏɴʟʏ.", show_alert=True); return

    try:
        _, user_id_str, file_name = call.data.split('_', 2)
        user_id = int(user_id_str)

        user_pending = pending_approvals.get(user_id, [])
        file_info = next((f for f in user_pending if f[0] == file_name), None)
        if not file_info:
            bot.answer_callback_query(call.id, "⚠️ 𝐀ʟʀᴇᴀᴅʏ ᴩʀᴏᴄᴇꜱꜱᴇᴅ.", show_alert=True)
            return

        file_type = file_info[1]
        user_folder = get_user_folder(user_id)
        file_path = os.path.join(user_folder, file_name)

        if not os.path.exists(file_path):
            bot.answer_callback_query(call.id, f"⚠️ 𝐅ɪʟᴇ ᴍɪꜱꜱɪɴɢ!", show_alert=True)
            remove_pending_file(user_id, file_name)
            return

        bot.answer_callback_query(call.id, "✅ 𝐀ᴩᴩʀᴏᴠᴇᴅ!")

        remove_pending_file(user_id, file_name)
        save_user_file(user_id, file_name, file_type)

        try:
            bot.send_message(
                user_id,
                f"✅ 𝐘ᴏᴜʀ ꜰɪʟᴇ `{file_name}` ᴡᴀꜱ **𝐀𝐏𝐏𝐑𝐎𝐕𝐄𝐃** ʙʏ ᴀᴅᴍɪɴ!\n🚀 𝐒ᴛᴀʀᴛɪɴɢ ɴᴏᴡ...",
                parse_mode='Markdown'
            )
        except Exception as e:
            logger.error(f"Failed to notify user: {e}")

        if file_type == 'py':
            threading.Thread(target=run_script, args=(file_path, user_id, user_folder, file_name, call.message)).start()
        elif file_type == 'js':
            threading.Thread(target=run_js_script, args=(file_path, user_id, user_folder, file_name, call.message)).start()

        try:
            bot.edit_message_text(
                f"✅ **𝐀𝐏𝐏𝐑𝐎𝐕𝐄𝐃**\n\n"
                f"👤 𝐔ꜱᴇʀ: `{user_id}`\n"
                f"📁 𝐅ɪʟᴇ: `{file_name}`\n"
                f"👮 𝐁ʏ: `{call.from_user.id}`\n"
                f"⏰ {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
                call.message.chat.id, call.message.message_id,
                parse_mode='Markdown'
            )
        except Exception as e:
            logger.error(f"Error editing: {e}")
    except Exception as e:
        logger.error(f"Error in approve: {e}", exc_info=True)
        bot.answer_callback_query(call.id, "Error.", show_alert=True)

def reject_file_callback(call):
    if call.from_user.id not in admin_ids:
        bot.answer_callback_query(call.id, "⚠️ 𝐀ᴅᴍɪɴ ᴏɴʟʏ.", show_alert=True); return

    try:
        _, user_id_str, file_name = call.data.split('_', 2)
        user_id = int(user_id_str)

        bot.answer_callback_query(call.id, "❌ 𝐑ᴇᴊᴇᴄᴛᴇᴅ!")

        remove_pending_file(user_id, file_name)
        user_folder = get_user_folder(user_id)
        file_path = os.path.join(user_folder, file_name)
        if os.path.exists(file_path):
            try: os.remove(file_path)
            except Exception as e: logger.error(f"Error: {e}")

        try:
            bot.send_message(
                user_id,
                f"❌ 𝐘ᴏᴜʀ ꜰɪʟᴇ `{file_name}` ᴡᴀꜱ **𝐑𝐄𝐉𝐄𝐂𝐓𝐄𝐃** ʙʏ ᴀᴅᴍɪɴ.",
                parse_mode='Markdown'
            )
        except Exception as e:
            logger.error(f"Failed to notify user: {e}")

        try:
            bot.edit_message_text(
                f"❌ **𝐑𝐄𝐉𝐄𝐂𝐓𝐄𝐃**\n\n"
                f"👤 𝐔ꜱᴇʀ: `{user_id}`\n"
                f"📁 𝐅ɪʟᴇ: `{file_name}`\n"
                f"👮 𝐁ʏ: `{call.from_user.id}`\n"
                f"⏰ {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
                call.message.chat.id, call.message.message_id,
                parse_mode='Markdown'
            )
        except Exception as e:
            logger.error(f"Error editing: {e}")
    except Exception as e:
        logger.error(f"Error in reject: {e}", exc_info=True)
        bot.answer_callback_query(call.id, "Error.", show_alert=True)

def pending_files_callback(call):
    if call.from_user.id not in admin_ids:
        bot.answer_callback_query(call.id, "⚠️ 𝐀ᴅᴍɪɴ ᴏɴʟʏ.", show_alert=True); return

    bot.answer_callback_query(call.id)
    if not pending_approvals:
        try:
            bot.edit_message_text("✅ 𝐍ᴏ ᴩᴇɴᴅɪɴɢ ꜰɪʟᴇꜱ.", call.message.chat.id, call.message.message_id,
                                  reply_markup=create_main_menu_inline(call.from_user.id))
        except Exception as e: logger.error(f"Error: {e}")
        return

    text = "📋 **𝐏ᴇɴᴅɪɴɢ 𝐀ᴩᴩʀᴏᴠᴀʟꜱ:**\n\n"
    markup = types.InlineKeyboardMarkup(row_width=2)
    for uid, files in pending_approvals.items():
        text += f"👤 𝐔ꜱᴇʀ `{uid}`:\n"
        for f in files:
            text += f"  • `{f[0]}` ({f[1]})\n"
            markup.row(
                types.InlineKeyboardButton(f"✅ {f[0][:20]}", callback_data=f'approve_{uid}_{f[0]}'),
                types.InlineKeyboardButton(f"❌ 𝐑ᴇᴊᴇᴄᴛ", callback_data=f'reject_{uid}_{f[0]}')
            )
        text += "\n"
    markup.add(types.InlineKeyboardButton("🔙 𝐁ᴀᴄᴋ ᴛᴏ 𝐌ᴀɪɴ", callback_data='back_to_main'))
    try:
        bot.edit_message_text(text, call.message.chat.id, call.message.message_id,
                              reply_markup=markup, parse_mode='Markdown')
    except Exception as e: logger.error(f"Error: {e}")

# --- Cleanup ---
def cleanup():
    logger.warning("Shutdown. Cleaning up...")
    script_keys_to_stop = list(bot_scripts.keys())
    if not script_keys_to_stop: return
    for key in script_keys_to_stop:
        if key in bot_scripts: kill_process_tree(bot_scripts[key])
atexit.register(cleanup)

# --- Main Execution ---
if __name__ == '__main__':
    logger.info("="*40 + "\n🤖 Bot Starting Up...\n" +
                f"✅ Admin Approval: {'ON' if ADMIN_APPROVAL_REQUIRED else 'OFF'}\n" + "="*40)
    keep_alive()
    logger.info("🚀 Starting polling...")
    while True:
        try:
            bot.infinity_polling(logger_level=logging.INFO, timeout=60, long_polling_timeout=30)
        except requests.exceptions.ReadTimeout: logger.warning("ReadTimeout. Restarting in 5s..."); time.sleep(5)
        except requests.exceptions.ConnectionError as ce: logger.error(f"ConnectionError: {ce}. Retrying in 15s..."); time.sleep(15)
        except Exception as e:
            logger.critical(f"💥 Error: {e}", exc_info=True)
            time.sleep(30)
        finally: logger.warning("Polling finished. Restarting..."); time.sleep(1)
