import request from '@/utils/request';
import { createPersonalizationApi } from '../aiPersonalization.js';

export const personalizationApi = createPersonalizationApi(request);
