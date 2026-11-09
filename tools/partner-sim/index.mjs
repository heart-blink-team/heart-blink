#!/usr/bin/env node
// Partner simulator: acts as the second car against the HeartBlink Supabase backend,
// so one PC with one emulator can test the whole couple flow.

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(here, '..', '..');

const HELP = `하트깜빡이 상대 차 시뮬레이터

사용법: node index.mjs <명령> [인자] [--session 파일]

  profile <이름> <차번호>                 내(상대 차) 이름과 번호판 등록, 예: profile 민지 34나5678
  request <차번호>                        그 번호의 차에 연결 요청
  accept <요청한 차에 붙일 애칭>          받은 연결 요청 수락
  decline                                 받은 연결 요청 거절
  nickname <애칭> [first-met YYYY-MM-DD]  상대 번호판 애칭, 처음 만난 날 변경
  answer <글> [--audio 파일.m4a]          지금 질문에 답하기
  send <조건묶음> <조건값> <글> [옵션]    메시지 남기기
        옵션: --audio 파일.m4a  --opens-from 2026-11-07T09:00:00+09:00
              --place-name 이름 --lat 37.5 --lng 127.0 --radius 200 (조건묶음 place일 때)
        조건묶음/조건값:
          vehicle      wiper, headlight, seat_heater, aircon, window
          time         dawn, morning, noon, afternoon, evening, night
          arrival      destination, parked
          first_start  first_start
          place        place
  open <메시지 id>                        받은 메시지를 열고 들은 것으로 표시
  end [delete]                            연결 끊기 (delete를 붙이면 기록도 지움)
  drive on|off                            운전 중 표시 켜기, 끄기
  distance <km>                           오늘 달린 거리 더하기
  status                                  연결, 질문, 상대 답 공개 여부, 안 읽은 메시지 보기

  --session 파일   익명 세션 파일 (기본: .session.json). 다른 파일을 주면 다른 차가 됩니다.
  --help           이 도움말

설정: ${join(projectRoot, 'local.properties')} 의 SUPABASE_URL, SUPABASE_ANON_KEY 를 읽습니다.`;

const ERROR_TEXT = {
  already_connected: '이미 연결된 차가 있어요',
  plate_not_found: '그 번호로 가입한 차가 없어요',
  own_plate: '내 차 번호예요. 상대 차 번호를 넣어 주세요',
  partner_busy: '상대가 이미 다른 차와 연결되어 있어요',
  no_request: '받은 연결 요청이 없어요',
  not_connected: '아직 연결된 차가 없어요',
  profiles_plate_key: '이미 다른 차가 쓰고 있는 번호예요',
  profiles_plate_check: '차 번호를 12가3456처럼 넣어 주세요',
  profiles_name_check: '이름은 1자에서 20자까지 쓸 수 있어요',
  nickname_check: '애칭은 5자까지 쓸 수 있어요',
  body_check: '글은 200자까지 쓸 수 있어요',
  'row-level security': '지금은 할 수 없는 일이에요',
};

const GROUPS = {
  vehicle: ['wiper', 'headlight', 'seat_heater', 'aircon', 'window'],
  time: ['dawn', 'morning', 'noon', 'afternoon', 'evening', 'night'],
  arrival: ['destination', 'parked'],
  first_start: ['first_start'],
  place: ['place'],
};

class UserError extends Error {}

// ------------------------------------------------------------ args

function parseArgs(argv) {
  const positional = [];
  const options = {};
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--help' || a === '-h') options.help = true;
    else if (a.startsWith('--')) {
      const key = a.slice(2);
      const value = argv[i + 1];
      if (value === undefined || value.startsWith('--')) throw new UserError(`${a} 뒤에 값이 필요해요`);
      options[key] = value;
      i++;
    } else positional.push(a);
  }
  return { positional, options };
}

// ------------------------------------------------------------ config and session

function readLocalProperties() {
  const file = join(projectRoot, 'local.properties');
  if (!existsSync(file)) throw new UserError(`${file} 파일이 없어요`);
  const props = {};
  for (const raw of readFileSync(file, 'utf8').split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#') || line.startsWith('!')) continue;
    const m = line.match(/^([^=:\s]+)\s*[=:]\s*(.*)$/);
    if (!m) continue;
    // java properties escape ':' and '=' with a backslash
    props[m[1]] = m[2].replace(/\\([:=\\])/g, '$1').trim();
  }
  const url = props.SUPABASE_URL ?? '';
  const key = props.SUPABASE_ANON_KEY ?? '';
  if (!url || !key) throw new UserError('local.properties 에 SUPABASE_URL, SUPABASE_ANON_KEY 를 넣어 주세요');
  return { url, key };
}

/** Auth storage for supabase-js backed by one JSON file, so the anonymous user survives between runs. */
function fileStorage(file) {
  const load = () => (existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')) : {});
  const save = (data) => writeFileSync(file, JSON.stringify(data, null, 2));
  return {
    getItem: (k) => load()[k] ?? null,
    setItem: (k, v) => { const d = load(); d[k] = v; save(d); },
    removeItem: (k) => { const d = load(); delete d[k]; save(d); },
  };
}

async function connect(sessionFile) {
  const { url, key } = readLocalProperties();
  const { createClient } = await import('@supabase/supabase-js');
  const supabase = createClient(url, key, {
    auth: {
      storage: fileStorage(sessionFile),
      persistSession: true,
      autoRefreshToken: false,
      detectSessionInUrl: false,
    },
  });
  const { data: { session }, error } = await supabase.auth.getSession();
  if (error) throw error;
  let user = session?.user;
  if (!user) {
    const res = await supabase.auth.signInAnonymously();
    if (res.error) throw res.error;
    user = res.data.user;
    console.log(`새 익명 사용자로 로그인했어요 (${user.id})`);
  }
  return { supabase, me: user.id };
}

// ------------------------------------------------------------ helpers

function explain(error) {
  const text = [error?.message, error?.details, error?.hint].filter(Boolean).join(' ');
  for (const [code, korean] of Object.entries(ERROR_TEXT)) {
    if (text.includes(code)) return `${korean} (${code})`;
  }
  return text || String(error);
}

function must({ data, error }) {
  if (error) throw new UserError(explain(error));
  return data;
}

async function rpc(supabase, fn, args) {
  return must(await supabase.rpc(fn, args));
}

async function currentCouple(supabase, me) {
  const rows = must(await supabase.from('couples').select('*')
    .or(`requester.eq.${me},partner.eq.${me}`)
    .in('status', ['pending', 'active'])
    .order('created_at', { ascending: false })
    .limit(1));
  return rows[0] ?? null;
}

async function uploadVoice(supabase, coupleId, file) {
  if (!existsSync(file)) throw new UserError(`${file} 파일이 없어요`);
  const path = `${coupleId}/${randomUUID()}.m4a`;
  must(await supabase.storage.from('voice').upload(path, readFileSync(file), {
    contentType: 'audio/mp4',
    upsert: false,
  }));
  return path;
}

function partnerOf(couple, me) {
  return couple.requester === me ? couple.partner : couple.requester;
}

function needArgs(positional, n, usage) {
  if (positional.length < n) throw new UserError(`사용법: ${usage}`);
}

// ------------------------------------------------------------ commands

const commands = {
  async profile({ supabase }, [name, ...plateParts]) {
    const plate = plateParts.join('');
    const p = await rpc(supabase, 'save_profile', { p_name: name, p_plate: plate });
    console.log(`등록했어요: ${p.name} / ${p.plate}`);
  },

  async request({ supabase }, plateParts) {
    const c = await rpc(supabase, 'request_connection', { p_partner_plate: plateParts.join('') });
    console.log(`연결 요청을 보냈어요 (couple ${c.id}, ${c.status})`);
  },

  async accept({ supabase }, nickParts) {
    const c = await rpc(supabase, 'respond_connection', {
      p_accept: true,
      p_nickname_for_requester: nickParts.join(' '),
    });
    console.log(`연결됐어요 (couple ${c.id}), 상대 번호판 애칭: ${c.requester_nickname ?? '없음'}`);
  },

  async decline({ supabase }) {
    await rpc(supabase, 'respond_connection', { p_accept: false, p_nickname_for_requester: '' });
    console.log('연결 요청을 거절했어요');
  },

  async nickname({ supabase }, args, options) {
    let firstMet = options['first-met'] ?? null;
    const words = [];
    for (let i = 0; i < args.length; i++) {
      if (args[i] === 'first-met') { firstMet = args[i + 1] ?? null; i++; }
      else if (/^\d{4}-\d{2}-\d{2}$/.test(args[i])) firstMet = args[i];
      else words.push(args[i]);
    }
    if (firstMet && !/^\d{4}-\d{2}-\d{2}$/.test(firstMet)) throw new UserError('처음 만난 날은 YYYY-MM-DD 로 넣어 주세요');
    const c = await rpc(supabase, 'update_couple', {
      p_partner_nickname: words.join(' ') || null,
      p_first_met: firstMet,
    });
    console.log(`바꿨어요: 애칭 ${c.requester_nickname ?? '-'} / ${c.partner_nickname ?? '-'}, 처음 만난 날 ${c.first_met ?? '-'}`);
  },

  async answer({ supabase, me }, textParts, options) {
    const couple = await currentCouple(supabase, me);
    if (!couple || couple.status !== 'active') throw new UserError(ERROR_TEXT.not_connected);
    const audioPath = options.audio ? await uploadVoice(supabase, couple.id, options.audio) : null;
    const answeredNo = couple.question_no;
    const c = await rpc(supabase, 'answer_question', {
      p_body: textParts.join(' '),
      p_audio_path: audioPath,
      p_audio_ms: options['audio-ms'] ? Number(options['audio-ms']) : null,
    });
    console.log(`${answeredNo}번 질문에 답했어요${c.question_no !== answeredNo ? `, 둘 다 답해서 ${c.question_no}번 질문이 열렸어요` : ''}`);
  },

  async send({ supabase, me }, args, options) {
    const [group, value, ...textParts] = args;
    if (!GROUPS[group]) throw new UserError(`조건묶음은 ${Object.keys(GROUPS).join(', ')} 중 하나예요`);
    if (!GROUPS[group].includes(value)) throw new UserError(`${group} 의 조건값은 ${GROUPS[group].join(', ')} 중 하나예요`);
    const couple = await currentCouple(supabase, me);
    if (!couple || couple.status !== 'active') throw new UserError(ERROR_TEXT.not_connected);
    const row = {
      couple_id: couple.id,
      sender: me,
      receiver: partnerOf(couple, me),
      condition_group: group,
      condition_value: value,
      body: textParts.join(' '),
    };
    if (group === 'place') {
      if (options.lat === undefined || options.lng === undefined) throw new UserError('place 메시지는 --lat, --lng 가 필요해요');
      row.place_name = options['place-name'] ?? null;
      row.place_lat = Number(options.lat);
      row.place_lng = Number(options.lng);
      row.place_radius_m = Number(options.radius ?? 200);
    }
    if (options['opens-from']) row.opens_from = new Date(options['opens-from']).toISOString();
    if (options.audio) {
      row.audio_path = await uploadVoice(supabase, couple.id, options.audio);
      if (options['audio-ms']) row.audio_ms = Number(options['audio-ms']);
    }
    const m = must(await supabase.from('messages').insert(row).select().single());
    console.log(`메시지를 남겼어요 (${m.id}): [${group}/${value}] ${m.body}${m.audio_path ? ' + 음성' : ''}`);
  },

  async end({ supabase }, [mode]) {
    await rpc(supabase, 'end_couple', { p_delete_records: mode === 'delete' });
    console.log(mode === 'delete' ? '연결을 끊고 기록을 지웠어요' : '연결을 끊었어요');
  },

  async open({ supabase }, [id]) {
    await rpc(supabase, 'mark_message', { p_id: id, p_opened: true, p_heard: true });
    console.log('열고 들은 것으로 표시했어요');
  },

  async drive({ supabase }, [onOff]) {
    if (onOff !== 'on' && onOff !== 'off') throw new UserError('사용법: drive on|off');
    await rpc(supabase, 'set_driving', { p_driving: onOff === 'on' });
    console.log(onOff === 'on' ? '운전 중으로 표시했어요' : '운전 중 표시를 껐어요');
  },

  async distance({ supabase }, [km]) {
    const n = Number(km);
    if (!Number.isFinite(n) || n < 0) throw new UserError('사용법: distance <km> (0 이상 숫자)');
    await rpc(supabase, 'add_distance', { p_km: n });
    console.log(`오늘 거리에 ${n}km 더했어요`);
  },

  async status({ supabase, me }) {
    const profile = must(await supabase.from('profiles').select('*').eq('id', me)).at(0);
    console.log(`나: ${profile ? `${profile.name} / ${profile.plate}` : '(프로필 없음, profile 명령으로 등록)'}  [${me}]`);

    const couple = await currentCouple(supabase, me);
    if (!couple) {
      console.log('연결: 없음');
      return;
    }
    const partnerId = partnerOf(couple, me);
    const partner = must(await supabase.from('profiles').select('*').eq('id', partnerId)).at(0);
    const iAmRequester = couple.requester === me;
    console.log(`연결: ${couple.status} (couple ${couple.id}), ${iAmRequester ? '내가 요청함' : '상대가 요청함'}`);
    console.log(`상대: ${partner ? `${partner.name} / ${partner.plate}` : partnerId}`);
    console.log(`내 번호판 애칭(상대가 지어 줌): ${(iAmRequester ? couple.requester_nickname : couple.partner_nickname) ?? '-'}`);
    console.log(`상대 번호판 애칭(내가 지어 줌): ${(iAmRequester ? couple.partner_nickname : couple.requester_nickname) ?? '-'}`);
    console.log(`처음 만난 날: ${couple.first_met ?? '-'}`);
    if (couple.status !== 'active') return;

    const drive = must(await supabase.from('drive_status').select('*').eq('user_id', partnerId)).at(0);
    console.log(`상대 운전 중: ${drive ? (drive.driving ? '예' : '아니오') : '알 수 없음'}`);
    const km = await rpc(supabase, 'couple_distance', {});
    console.log(`연결 후 함께 달린 거리: ${Number(km ?? 0)}km`);

    const q = must(await supabase.from('questions').select('*').eq('no', couple.question_no)).at(0);
    const answers = must(await supabase.from('answers').select('*')
      .eq('couple_id', couple.id).eq('question_no', couple.question_no));
    const mine = answers.find((a) => a.user_id === me);
    const theirs = answers.find((a) => a.user_id !== me);
    console.log(`\n지금 질문 ${couple.question_no}번: ${q?.body ?? '?'}`);
    console.log(`  내 답: ${mine ? mine.body || '(음성만)' : '(아직)'}`);
    console.log(`  상대 답: ${theirs ? theirs.body || '(음성만)' : '(둘 다 답하기 전에는 보이지 않아요)'}`);
    if (couple.question_no > 1) {
      const prev = must(await supabase.from('answers').select('*')
        .eq('couple_id', couple.id).eq('question_no', couple.question_no - 1));
      const other = prev.find((a) => a.user_id !== me);
      if (other) console.log(`  지난 질문(${couple.question_no - 1}번) 상대 답: ${other.body || '(음성만)'}`);
    }

    const unread = must(await supabase.from('messages').select('*')
      .eq('receiver', me).is('opened_at', null).order('created_at', { ascending: true }));
    console.log(`\n안 읽은 메시지: ${unread.length}개`);
    for (const m of unread) {
      const place = m.condition_group === 'place' ? ` @${m.place_name ?? ''}(${m.place_lat}, ${m.place_lng})` : '';
      console.log(`  ${m.id}  [${m.condition_group}/${m.condition_value}${place}] ${m.body}${m.audio_path ? ' + 음성' : ''}  (열림 가능: ${m.opens_from})`);
    }
    const sent = must(await supabase.from('messages').select('*')
      .eq('sender', me).order('created_at', { ascending: false }).limit(5));
    if (sent.length) {
      console.log('\n내가 보낸 최근 메시지:');
      for (const m of sent) {
        console.log(`  ${m.id}  [${m.condition_group}/${m.condition_value}] ${m.body}  열림 ${m.opened_at ? '예' : '아니오'}, 들음 ${m.heard_at ? '예' : '아니오'}`);
      }
    }
  },
};

// ------------------------------------------------------------ main

async function main() {
  const { positional, options } = parseArgs(process.argv.slice(2));
  const [name, ...args] = positional;
  if (options.help || !name) {
    console.log(HELP);
    return;
  }
  const command = commands[name];
  if (!command) throw new UserError(`모르는 명령이에요: ${name}\n도움말: node index.mjs --help`);

  const usage = {
    profile: ['profile <이름> <차번호>', 2],
    request: ['request <차번호>', 1],
    accept: ['accept <애칭>', 1],
    nickname: ['nickname <애칭> [first-met YYYY-MM-DD]', 1],
    answer: ['answer <글> [--audio 파일.m4a]', 1],
    send: ['send <조건묶음> <조건값> <글>', 3],
    open: ['open <메시지 id>', 1],
    drive: ['drive on|off', 1],
    distance: ['distance <km>', 1],
  }[name];
  if (usage) needArgs(args, usage[1], usage[0]);

  const sessionFile = resolve(here, options.session ?? '.session.json');
  const ctx = await connect(sessionFile);
  await command(ctx, args, options);
}

main().then(
  () => process.exit(0),
  (e) => {
    console.error(e instanceof UserError ? e.message : `오류: ${explain(e)}`);
    process.exit(1);
  },
);
